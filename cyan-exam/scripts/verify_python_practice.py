"""Verify local Python practice questions against the configured go-judge sandbox.

Requires MYSQL_PWD and CYAN_SANDBOX_TOKEN_FILE; MYSQL_EXE and CYAN_SANDBOX_URL
can override the local defaults. Reference solutions live here, not in the bank.
"""
import json
import os
import subprocess
import urllib.request
from pathlib import Path


SOLUTIONS = {
    "01": "a, b = map(int, input().split())\nprint(a + b)\n",
    "02": (
        "n = int(input())\nnumbers = list(map(int, input().split()))\n"
        "print(sum(value for value in numbers if value % 2 == 0))\n"
    ),
    "03": (
        "words = input().split()\ntarget = input().strip()\n"
        "print(words.count(target))\n"
    ),
    "04": (
        "n = int(input())\nnumbers = list(map(int, input().split()))\n"
        "best = current = 1\n"
        "for i in range(1, n):\n"
        "    current = current + 1 if numbers[i] > numbers[i - 1] else 1\n"
        "    best = max(best, current)\n"
        "print(best)\n"
    ),
}


def load_questions():
    mysql = os.getenv("MYSQL_EXE", r"D:\app\mysql-8.0.45-winx64\bin\mysql.exe")
    query = (
        "SELECT JSON_OBJECT('marker',RIGHT(remark,2),'question_type',question_type,"
        "'status',status,'answer',answer,'initial_code',initial_code) "
        "FROM cyan_question WHERE remark LIKE 'Python练习:20261030:%' ORDER BY remark"
    )
    result = subprocess.run(
        [mysql, "-h", "127.0.0.1", "-u", "root", "--default-character-set=utf8mb4",
         "--batch", "--raw", "--skip-column-names",
         os.getenv("MYSQL_DATABASE", "cyan_exam"), "-e", query],
        check=True, capture_output=True, text=True, encoding="utf-8",
    )
    return [json.loads(line) for line in result.stdout.splitlines()]


def run_sandbox(source, stdin, token):
    url = os.getenv("CYAN_SANDBOX_URL", "http://127.0.0.1:15050").rstrip("/") + "/run"
    command = {
        "args": ["/usr/local/bin/python3", "-I", "-B", "/w/main.py"],
        "env": ["PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8", "HOME=/w"],
        "files": [{"content": stdin},
                  {"name": "stdout", "max": 32768, "pipe": True},
                  {"name": "stderr", "max": 32768, "pipe": True}],
        "copyIn": {"main.py": {"content": source}},
        "cpuLimit": 2_000_000_000,
        "clockLimit": 5_000_000_000,
        "memoryLimit": 134217728,
        "stackLimit": 67108864,
        "procLimit": 16,
        "copyOutMax": 65536,
    }
    request = urllib.request.Request(
        url, json.dumps({"cmd": [command]}).encode(),
        {"Authorization": "Bearer " + token, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)[0]


def main():
    token_file = Path(os.getenv("CYAN_SANDBOX_TOKEN_FILE", r"D:\app\cyan-exam\sandbox-token.key"))
    token = token_file.read_text(encoding="utf-8").strip()
    questions = load_questions()
    runs = 0
    assert len(questions) == len(SOLUTIONS), f"Expected {len(SOLUTIONS)} questions, got {len(questions)}"
    for question in questions:
        marker = question["marker"]
        assert marker in SOLUTIONS and question["question_type"] == "PYTHON"
        assert question["status"] == "0" and question["initial_code"]
        cases = json.loads(question["answer"])["cases"]
        assert len(cases) == 4
        assert sum(case["weight"] for case in cases if not case["public"]) == 10
        assert any(case["public"] and case["weight"] == 0 for case in cases)
        for index, case in enumerate(cases, 1):
            assert case["comparison"] == "EXACT"
            result = run_sandbox(SOLUTIONS[marker], case["input"], token)
            runs += 1
            actual = result.get("files", {}).get("stdout", "")
            assert result["status"] == "Accepted" and actual == case["expected"], (
                f"{marker} case {index}: status={result['status']}, "
                f"stdout={actual!r}, expected={case['expected']!r}"
            )
        for sample in (case for case in cases if case["public"]):
            source = "import sys\nsys.stdout.write(" + repr(sample["expected"]) + ")\n"
            score = 0
            for case in (case for case in cases if not case["public"]):
                result = run_sandbox(source, case["input"], token)
                runs += 1
                if result["status"] == "Accepted" and result.get("files", {}).get("stdout", "") == case["expected"]:
                    score += case["weight"]
            assert score < 10, f"{marker}: printing the sample output must not pass all hidden tests"
            print(f"{marker}: hardcoded sample output scores {score}/10, not a full pass")
        print(f"{marker}: {len(cases)} sandbox cases passed (hidden 10 points)")
    print(f"PASS: {len(questions)} questions, {runs} sandbox runs including hardcoded-output regressions")


if __name__ == "__main__":
    main()
