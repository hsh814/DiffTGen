import os
import sys
import json
from typing import List, Tuple, Dict
import difflib
import re

ROOTDIR = "/root/project/DiffTGen"

def get_diff(file_original: str, file_patched: str) -> List[int]:
  with open(file_original, "r") as fo, open(file_patched, "r") as fp:
    original_contents = fo.readlines()
    patched_contents = fp.readlines()
    diff = difflib.unified_diff(original_contents, patched_contents, n=0)
    index = 0
    diff_str = ""
    for line in diff:
      print(line)
      index += 1
      if index == 3:
        diff_str = line.strip()
        tokens = diff_str.split()
        before = tokens[1].split(",")
        original_line = int(before[0])
        if original_line < 0:
          original_line = -original_line
        rg = 1
        if len(before) > 1:
          rg = int(before[1])
        after = tokens[2].split(",")
        patched_line = int(after[0])
      elif index > 3:
        if line.startswith("+"):
          pass
        elif line.startswith("-"):
          pass
        pass
    # Print the numbers
    # print(numbers)
    # return numbers
  return [0,0,0,0]

def init_d4j(bugid: str, loc: str) -> None:
  proj, bid = bugid.split("-")
  os.system(f"defects4j checkout -p {proj} -v {bid}b -w {loc}")


def run(conf_file: str) -> None:
  with open(conf_file, 'r') as c:
    conf = json.load(c)
    bugid: str = conf["bugid"]
    proj = bugid.split("-")[0].lower()
    basedir = os.path.join(ROOTDIR, "patch", proj, bugid)
    plau_patch_list = conf["plausible_patches"]
    d4j_dir = os.path.join(ROOTDIR, "d4j", bugid)

    correct_patch = conf["correct_patch"]
    id = correct_patch["id"]
    location = correct_patch["location"]
    original_file = os.path.join(d4j_dir, correct_patch["file"])

    if not os.path.exists(original_file):
      init_d4j(bugid, d4j_dir)
    
    print(f"Correct patch {id}")
    get_diff(original_file, os.path.join(basedir, location))
    return
    for plau in plau_patch_list:
      print("===============================================")
      original_file = os.path.join(d4j_dir, plau["file"])
      id = plau["id"]
      location = plau["location"]
      print(f"Patch {id}")
      get_diff(original_file, os.path.join(basedir, location)) 


def main(args: List[str]) -> None:
  if len(args) == 1:
    run(os.path.join(ROOTDIR, "patch", "test", "chart-3.json"))
  elif len(args) == 2:
    run(args[1])
  elif len(args) > 2:
    if args[1] != "compare":
      print("Usage: python3 find-line.py <conf_file>")
      return
    else:
      get_diff(args[2], args[3])

if __name__ == "__main__":
  main(sys.argv)
