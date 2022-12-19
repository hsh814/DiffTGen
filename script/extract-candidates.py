import os
import sys
import json
from typing import List, Tuple, Dict


def get_plausible(sim: List[dict]) -> List[str]:
  plau_list = list()
  for loc in sim:
    res = sim[loc]
    if res["plausible"]:
      plau_list.append(loc)
  return plau_list


def get_info_recoder(sw: dict, correct_patches: List[str], plau_list: List[str]) -> Dict[str, dict]:
  result = dict()
  result["bugid"] = sw["project_name"]
  result["tool"] = "recoder"
  result["correct_patch"] = dict()
  plau_patches = list()
  result["plausible_patches"] = plau_patches
  for file_info in sw["rules"]:
    file_name = file_info["file"]
    for line_info in file_info["lines"]:
      for case_info in line_info["cases"]:
        loc = case_info["location"]
        tokens = loc.split("/")
        id = tokens[0] + "-" + tokens[1]
        patch_info = {"id": id, "location": loc, "file": file_name}
        if id in correct_patches:
          result["correct_patch"] = patch_info
          continue
        if loc in plau_list:
          plau_patches.append(patch_info)
  return result


def get_correct_patch_recoder(correct_patch_file: str) -> List[str]:
  with open(correct_patch_file, "r") as cf:
    result = dict()
    for line in cf.readlines():
      line = line.strip()
      if line.startswith('#') or len(line) == 0:
        continue
      tokens = line.split(",")
      bid = tokens[0]
      correct_patches = tokens[1:]
      result[bid] = correct_patches
    return result


def main_recoder(args: List[str]) -> None:
  rootdir = args[1]
  outdir = args[2]
  correct_map = get_correct_patch_recoder(
      os.path.join(rootdir, "data", "correct_patch.csv"))
  for bugid in correct_map:
    os.makedirs(f"{outdir}/{bugid}", exist_ok=True)
    switch_info_file = os.path.join(rootdir, "d4j", bugid, "switch-info.json")
    sim_file = os.path.join(rootdir, "sim", bugid, f"{bugid}-sim.json")
    correct_patches = correct_map[bugid]
    with open(switch_info_file, "r") as swf, open(sim_file, "r") as sf:
      sw = json.load(swf)
      sim = json.load(sf)
      plau_list = get_plausible(sim)
      result = get_info_recoder(sw, correct_patches, plau_list)
      with open(f"{outdir}/{bugid}.json", "w") as f:
        json.dump(result, f, indent=2)
    for plau in plau_list:
      original = os.path.join(rootdir, "d4j", bugid, plau)
      target = os.path.join(outdir, bugid, plau)
      os.makedirs(os.path.dirname(target), exist_ok=True)
      os.system(f"cp {original} {target}")

if __name__ == "__main__":
  main_recoder(sys.argv)
