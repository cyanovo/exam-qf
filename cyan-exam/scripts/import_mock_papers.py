"""Generate an idempotent question-bank import from the supplied mock papers.

Requires python-docx. The resulting SQL is reviewed and applied to a chosen DB
explicitly; this script never connects to a database.
"""
import json
import re
from pathlib import Path

from docx import Document

SOURCE = Path("D:/工作任务/yanshee教学文档/资料类/模拟卷")
OUTPUT = Path(__file__).resolve().parents[1] / "sql" / "mock_papers_question_bank.sql"
PAPERS = {
    "A": "程序设计模拟卷A.docx",
    "B": "程序设计模拟卷B.docx",
    "C": "程序设计模拟卷C.docx",
    "原第三套": "程序设计模拟卷.docx",
}
ANSWERS = {
    "A": ("BCBCBACABCAACBA", "对错对对对"),
    "B": ("CBABDCCCDDBBDAA", "错错对对对"),
    "C": ("ABABACCCBCADADA", "对错错对对"),
    "原第三套": ("ABABACCCBCADADA", "对错错对对"),
}
# The supplied answer key contradicts the question or lacks a unique correct option.
REVIEW = {
    ("A", "单选", 9): "答案表选B，但顺序/循环/分支对应A",
    ("B", "单选", 9): "答案表选D，但无限循环应选B",
    ("C", "单选", 1): "A与B均是浮点数表示，单选不唯一",
    ("C", "单选", 14): "CSV的标准英文为Comma-Separated Values，四项均不准确",
    ("C", "单选", 15): "文件关闭状态应检查file.closed，四项均不准确",
    ("原第三套", "单选", 1): "A与B均是浮点数表示，单选不唯一",
    ("原第三套", "单选", 14): "CSV的标准英文为Comma-Separated Values，四项均不准确",
    ("原第三套", "单选", 15): "文件关闭状态应检查file.closed，四项均不准确",
}


def sql(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def extract(label, filename):
    paragraphs = [p.text.strip() for p in Document(SOURCE / filename).paragraphs if p.text.strip()]
    section = None
    choice = []
    judges = []
    tasks = []
    current = None
    for text in paragraphs:
        if text.startswith("一、"):
            section = "choice"
        elif text.startswith("二、"):
            section = "judge"
        elif text.startswith("三、"):
            section = "task"
        elif section == "choice":
            match = re.match(r"^(\d+)\.\s*(.+)$", text)
            option = re.match(r"^([A-D])\.\s*(.+)$", text)
            if match:
                choice.append({"title": match.group(2), "options": []})
            elif option and choice:
                choice[-1]["options"].append((option.group(1), option.group(2)))
        elif section == "judge":
            judges.append(re.sub(r"^\d+\.\s*", "", text).strip())
        elif section == "task":
            if "请编写一段代码完成" in text:
                if current:
                    tasks.append(current)
                name = re.search(r"“([^”]+)”", text)
                points = re.search(r"满分\s*(\d+)\s*分", text)
                if not name or not points:
                    raise ValueError(f"Cannot parse task heading: {label}: {text}")
                current = {"name": name.group(1), "heading": text, "score": int(points.group(1)), "steps": []}
            elif current:
                current["steps"].extend(
                    (part.strip() for part in re.split(r"(?=\s+\(\d+\))", text) if part.strip())
                )
    if current:
        tasks.append(current)
    if len(choice) != 15 or any(len(q["options"]) != 4 for q in choice) or len(judges) != 5 or len(tasks) != 3:
        raise ValueError(f"Unexpected paper layout: {label}: {len(choice)}, {len(judges)}, {len(tasks)}")
    return choice, judges, tasks


def main():
    records = {}
    for label, filename in PAPERS.items():
        choices, judges, tasks = extract(label, filename)
        choice_answers, judge_answers = ANSWERS[label]
        for index, question in enumerate(choices, 1):
            answer = choice_answers[index - 1]
            options = [{"label": key, "content": value, "isAnswer": int(key == answer)}
                       for key, value in question["options"]]
            record = ("SINGLE_CHOICE", question["title"], json.dumps(options, ensure_ascii=False), answer)
            add(records, record, label, "单选", index, 2, REVIEW.get((label, "单选", index)))
        for index, title in enumerate(judges, 1):
            answer = "A" if judge_answers[index - 1] == "对" else "B"
            options = [{"label": "A", "content": "对", "isAnswer": int(answer == "A")},
                       {"label": "B", "content": "错", "isAnswer": int(answer == "B")}]
            record = ("JUDGE", title, json.dumps(options, ensure_ascii=False), answer)
            add(records, record, label, "判断", index, 2, None)
        for index, task in enumerate(tasks, 1):
            items = []
            for step in task["steps"]:
                score = re.search(r"（(\d+)分）", step)
                if not score:
                    raise ValueError(f"Missing task score: {label}: {step}")
                items.append({"title": re.sub(r"^\(\d+\)\s*", "", step[:score.start()]).strip()[:120],
                              "maxScore": int(score.group(1))})
            if sum(item["maxScore"] for item in items) != task["score"]:
                raise ValueError(f"Task score mismatch: {label}: {task['name']}")
            record = ("ROBOT_PRACTICAL", task["name"], "\n".join([task["heading"], *task["steps"]]),
                      json.dumps(items, ensure_ascii=False))
            add(records, record, label, "实操", index, task["score"], None)

    lines = [
        "-- Review before applying to the intended database. No exam or student records are modified.",
        "-- Re-running inserts only missing source markers; disabled questions require teacher review.",
        "-- ROBOT_PRACTICAL: sub_title=task instructions; answer=onsite rubric JSON (not Python test cases).",
        "START TRANSACTION;",
    ]
    for record in records.values():
        typ, title, subtitle, answer = record["data"]
        marker = record["marker"]
        remark = f"{marker}；来源：{', '.join(record['sources'])}"
        if record["issues"]:
            remark += "；待核对：" + "；".join(record["issues"])
        difficulty = 1 if record["score"] == 15 else 3 if record["score"] == 20 else 5 if record["score"] == 25 else 2
        status = "1" if record["issues"] else "0"
        values = [sql("PYTHON"), str(difficulty), sql(typ), sql(title), sql(subtitle),
                  sql(answer), sql(status), sql(remark)]
        lines.append(
            "INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)\n"
            f"SELECT {', '.join(values)} FROM DUAL\n"
            f"WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE {sql(marker + '；%')});"
        )
    lines.append("COMMIT;")
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    counts = {kind: sum(r["data"][0] == kind for r in records.values())
              for kind in ("SINGLE_CHOICE", "JUDGE", "ROBOT_PRACTICAL")}
    print(f"{counts}; disabled={sum(bool(r['issues']) for r in records.values())}; output={OUTPUT}")


def add(records, data, label, kind, index, score, issue):
    # The same task in the standalone and B papers is one reusable bank question.
    key = data if data[0] == "ROBOT_PRACTICAL" else data[:3]
    if key not in records:
        records[key] = {"data": data, "marker": f"模拟卷:{label}:{kind}:{index:02d}",
                        "sources": [], "score": score, "issues": []}
    current = records[key]
    if current["data"][3] != data[3]:
        current["issues"].append(f"{label}{kind}{index}的答案与其他版本不一致")
    current["sources"].append(f"{label}{kind}{index}")
    if issue and issue not in current["issues"]:
        current["issues"].append(issue)


if __name__ == "__main__":
    main()
