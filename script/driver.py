import os
import sys
import json
from typing import List, Tuple, Dict
import difflib
import re
import subprocess
import time
import multiprocessing as mp

ROOTDIR = "/root/project/DiffTGen"
manager = mp.Manager()
global_cmd_queue = manager.Queue()

def get_cn(line: str) -> int:
  ln = line.strip()
  return line.find(ln)

def get_diff(file_original: str, file_patched: str, file_original_oracle: str) -> None:
  with open(file_original, "r") as fo, open(file_patched, "r") as fp:
    original_contents = fo.readlines()
    patched_contents = fp.readlines()
    diff = difflib.unified_diff(original_contents, patched_contents, n=0)
    index = 0
    diff_str = ""
    original_line = 0
    original_range = 1
    patched_line = 0
    patched_range = 1
    delta = list()
    for line in diff:
      print(line)
      index += 1
      if index == 3:
        diff_str = line.strip()
        tokens = diff_str.split()
        before = tokens[1].split(",")
        original_line = int(before[0])
        if original_line < 0:
          original_line = -1 * original_line
        if len(before) > 1:
          original_range = int(before[1])
        after = tokens[2].split(",")
        patched_line = int(after[0])
        if len(after) > 1:
          patched_range = int(after[1])
        break
      elif index > 3 and index < 3 + original_range:
        print("Original line")
        line_num = original_line + index - 4
        cn = get_cn(original_contents[line_num - 1])
        delta.append(f"{file_original}:{line_num},{cn}")
      elif index >= 3 + original_range:
        print("Patched line")
        line_num = patched_line + index - 4 - original_range
        cn = get_cn(patched_contents[line_num - 1])
        delta.append(f"{file_patched}:{line_num},{cn}")
    # Print the numbers
    if original_range == 0:
      cn = get_cn(original_contents[original_line - 1])
      delta.append(f"null({file_original}:{original_line},{cn};after)")
    else:
      cn = get_cn(original_contents[original_line - 1])
      delta.append(f"{file_original}:{original_line},{cn}")
    if patched_range == 0:
      cn = get_cn(patched_contents[patched_line - 1])
      delta.append(f"null({file_patched}:{patched_line},{cn};before)")
    else:
      cn = get_cn(patched_contents[patched_line - 1])
      delta.append(f"{file_patched}:{patched_line},{cn}")
    print(delta)

    # Oracle
    delta_oracle = list()
    cn = get_cn(original_contents[original_line - 1])
    if file_original != file_original_oracle:
      delta_oracle.append(f"null({file_original_oracle})")
    delta_oracle.append(f"{file_original}:{original_line},{cn}")
    return delta, delta_oracle
  return [0,0,0,0]

def run_cmd(cmd: List[str], cwd: str) -> bool:
  print("RUN_CMD: " + " ".join(cmd))
  if os.path.exists(cwd):
    print("RUN_CMD in " + cwd)
    proc = subprocess.run(cmd, cwd=cwd)
    if proc.returncode != 0:
      print(f"Abnormal exit with {proc.returncode}")
      return False
    return True
  else:
    print("CANNOT FIND DIR " + cwd)
  return False

def unzip_jar(cwd: str, jar_file: str) -> None:
  run_cmd(["jar", "-xf", jar_file], cwd)

def init_d4j(bugid: str, loc: str) -> None:
  proj, bid = bugid.split("-")
  print(f"Checkout {bugid}")
  run_cmd(["rm", "-rf", loc], ROOTDIR)
  run_cmd(["defects4j", "checkout", "-p", proj, "-v", bid + "b", "-w", loc], ROOTDIR)
  # os.system(f"defects4j checkout -p {proj} -v {bid}b -w {loc}")
  print(f"Compile {bugid}")
  run_cmd(["defects4j", "compile"], loc)
  # os.system(f"defects4j compile -w {loc}")
  run_cmd(["defects4j", "export", "-p", "dir.bin.classes", "-o", f"{loc}/builddir.txt"], loc)
  # os.system(f"defects4j export -p dir.bin.classes -w {loc} -o {loc}/builddir.txt")
  run_cmd(["defects4j", "export", "-p", "cp.compile", "-o", os.path.join(loc, "cp.txt")], loc)
  with open(f"{loc}/builddir.txt", 'r') as f:
    builddir = f.read().strip()
    tmp_dir = os.path.join(loc, builddir)
    run_cmd(["jar", "-cf", f"{loc}/{bugid}.jar", "."], tmp_dir)
  temp_deps = os.path.join(loc, "tmp_deps")
  os.makedirs(temp_deps, exist_ok=True)
  unzip_jar(temp_deps, f"{loc}/{bugid}.jar")
  with open(f"{loc}/cp.txt", 'r') as f:
    lines = f.read().strip().split(":")
    for line in lines:
      if line.endswith(".jar"):
        unzip_jar(temp_deps, line)
  run_cmd(["jar", "-cf", f"{loc}/{bugid}-with-deps.jar", "."], temp_deps)


def execute(cmd: List[str]) -> bool:
  print("RUN " + " ".join(cmd))
  start = time.time()
  proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  print(f"End with returncode {proc.returncode}")
  end = time.time()
  print(f"TOTAL TIME: {(end - start)/60} min")
  try:
    bugid = cmd[2]
    id = cmd[4]
    log = os.path.join(ROOTDIR, "log", bugid)
    os.makedirs(log, exist_ok=True)
    with open(os.path.join(log, id + ".log"), "w") as f:
      f.write(" ".join(cmd))
      f.write(f"\nEnd with returncode {proc.returncode}\n")
      f.write(f"TOTAL TIME: {(end - start)/60} min\n")
      f.write(proc.stdout.decode('utf-8'))
      f.write("\n\nerr:\n\n")
      f.write(proc.stderr.decode('utf-8'))
  except:
    pass
  return True

def filter_bugid(bugid: str) -> bool:
  bids = { 
    "Closure-63", "Closure-93",
    "Time-21", "Lang-2"
  }
  # return bugid != "Chart-1"
  return bugid in bids

def write_deltas(deltas: Tuple[List[str], List[str]], patch_file: str, oracle_file: str) -> None:
  with open(oracle_file, 'w') as o:
    for d in deltas[1]:
      o.write(d + "\n")
  with open(patch_file, 'w') as p:
    for d in deltas[0]:
      p.write(d + "\n")

def run(basedir: str, conf_file: str) -> List[List[str]]:
  with open(conf_file, 'r') as c:
    cmd_list = list()
    conf = json.load(c)
    bugid: str = conf["bugid"]
    if filter_bugid(bugid):
      return cmd_list
    tool = conf["tool"]
    plau_patch_list = conf["plausible_patches"]
    d4j_dir = os.path.join(ROOTDIR, "d4j", bugid)
    os.makedirs(os.path.join(ROOTDIR, "d4j"), exist_ok=True)
    correct_patch = conf["correct_patch"]
    id = correct_patch["id"]
    location = correct_patch["location"]
    correct_original_file = os.path.join(d4j_dir, correct_patch["file"])

    if not os.path.exists(correct_original_file):
      init_d4j(bugid, d4j_dir)
    
    print(f"Correct patch {id}")
    patched_file = os.path.join(basedir, bugid, location)

    for plau in plau_patch_list:
      print("===============================================")
      original_file = os.path.join(d4j_dir, plau["file"])
      id = plau["id"]
      location = plau["location"]
      print(f"Patch {id}")
      patched_file = os.path.join(basedir, bugid, location)
      deltas = get_diff(original_file, patched_file, correct_original_file)
      delta_file = os.path.join(os.path.dirname(patched_file), "delta.txt")
      oracle_file = os.path.join(os.path.dirname(patched_file), "oracle.txt")
      write_deltas(deltas, delta_file, oracle_file)
      deps = os.path.join(d4j_dir, "cp.txt")
      if not os.path.exists(deps):
        subprocess.run(["defects4j", "export", "-p", "cp.compile", "-o", deps, "-w", d4j_dir])
      cp = os.path.join(d4j_dir, bugid + "-with-deps.jar")
      # with open(deps, 'r') as d:
      #   lines = d.read().strip().split(":")
      #   for line in lines:
      #     if line.endswith(".jar"):
      #       cp += f":{line}"
      cmd = ["./run", "-bugid", bugid, "-repairtool", tool+id, 
        "-dependjpath", cp,
        "-outputdpath", os.path.join(ROOTDIR, "out", tool),
        "-inputfpath", delta_file, "-oracleinputfpath", oracle_file,
        "-stopifoverfittingfound", "-evosuitetimeout", "120", "-runparallel"
      ]
      if original_file != patched_file:
        continue
      cmd_list.append(cmd)
      # execute(cmd)
    return cmd_list

def sort_bugids(bugids: List[str]) -> List[str]:
    proj_dict = dict()
    for bugid in bugids:
        proj, id = bugid.split("-")
        if proj not in proj_dict:
            proj_dict[proj] = list()
        proj_dict[proj].append(int(id))
    projs = sorted(list(proj_dict.keys()))
    result = list()
    for proj in projs:
        ids = proj_dict[proj]
        ids.sort()
        for id in ids:
            result.append(f"{proj}-{id}")
    return result

def main(args: List[str]) -> None:
  if len(args) == 1:
    run(os.path.join(ROOTDIR, "patch", "test", "chart-3.json"))
  elif len(args) == 2:
    basedir = args[1]
    basedir = os.path.abspath(basedir)
    cmd_list = list()
    for bugid in sort_bugids(os.listdir(basedir)):
      dir = os.path.join(basedir, bugid)
      if os.path.isdir(dir):
        result = run(basedir, os.path.join(dir, f"{bugid}.json"))
        cmd_list.extend(result)
    pool = mp.Pool(processes=16)
    pool.map(execute, cmd_list)
    pool.close()
    pool.join()
  elif len(args) > 2:
    if args[1] != "compare":
      print("Usage: python3 find-line.py <conf_file>")
      return
    else:
      get_diff(args[2], args[3])

if __name__ == "__main__":
  main(sys.argv)
