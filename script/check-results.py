import os
import sys

ROOTDIR = "/root/project/DiffTGen"

def sort_bugids(bugids):
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

def main(args: list) -> None:
  outdir = args[1]
  dirs = os.listdir(outdir)
  results = dict()
  total_incorrect = 0
  total = 0
  for dir in dirs:
    bugid = dir.split("_")[0]
    if bugid not in results:
      results[bugid] = list()
    caseid = dir.replace(bugid, "", 1)
    testcasedir = os.path.join(outdir, dir, 'testcase')
    total += 1
    if len(os.listdir(testcasedir)) == 0:
      results[bugid].append({"id": caseid, "incorrect": False})
    else:
      total_incorrect += 1
      results[bugid].append({"id": caseid, "incorrect": True})
  print(f"TOTAL {total_incorrect} / {total}")
  bugids = results.keys()
  bugids = sort_bugids(bugids)
  csv_content = list()
  for bugid in bugids:
    for testcase in results[bugid]:
      csv_content.append(f"{bugid},{testcase['id']},{testcase['incorrect']}\n")
  with open(outdir + "/out.csv", "w") as f:
    f.writelines(csv_content) 

if __name__ == "__main__":
  main(sys.argv)