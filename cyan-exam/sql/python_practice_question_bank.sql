-- Ordinary Python auto-graded questions for local practice.
-- Run after exam_python_initial_code.sql and exam_grading_rules.sql.
-- Only inserts missing questions; never creates exams or student records.
START TRANSACTION;

SET @case_sum = JSON_OBJECT('cases', JSON_ARRAY(
  JSON_OBJECT('input', CONCAT('3 5', CHAR(10 USING utf8mb4)), 'expected', CONCAT('8', CHAR(10 USING utf8mb4)), 'weight', 0, 'public', TRUE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('-8 3', CHAR(10 USING utf8mb4)), 'expected', CONCAT('-5', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('0 0', CHAR(10 USING utf8mb4)), 'expected', CONCAT('0', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('1000000000 999999999', CHAR(10 USING utf8mb4)), 'expected', CONCAT('1999999999', CHAR(10 USING utf8mb4)), 'weight', 4, 'public', FALSE, 'comparison', 'EXACT')
));
INSERT INTO cyan_question (category, difficulty, question_type, title, sub_title, answer, initial_code, status, remark)
SELECT 'PYTHON', 1, 'PYTHON', '两数之和',
  '从标准输入读取一行，包含两个整数 a 和 b（-10^9 <= a,b <= 10^9），以空格分隔。输出 a+b，末尾换行。示例输入：3 5；示例输出：8。',
  @case_sum, CONCAT('a, b = map(int, input().split())', CHAR(10), '# 输出两数之和', CHAR(10)),
  '0', 'Python练习:20261030:01'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark = 'Python练习:20261030:01');
UPDATE cyan_question SET answer = @case_sum WHERE remark = 'Python练习:20261030:01'
  AND JSON_UNQUOTE(JSON_EXTRACT(answer, '$.cases[0].input')) LIKE 'base64:type15:%';

SET @case_even = JSON_OBJECT('cases', JSON_ARRAY(
  JSON_OBJECT('input', CONCAT('5', CHAR(10 USING utf8mb4), '1 2 3 4 6', CHAR(10 USING utf8mb4)), 'expected', CONCAT('12', CHAR(10 USING utf8mb4)), 'weight', 0, 'public', TRUE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('4', CHAR(10 USING utf8mb4), '-2 -3 0 7', CHAR(10 USING utf8mb4)), 'expected', CONCAT('-2', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('1', CHAR(10 USING utf8mb4), '9', CHAR(10 USING utf8mb4)), 'expected', CONCAT('0', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('6', CHAR(10 USING utf8mb4), '10 11 12 13 14 15', CHAR(10 USING utf8mb4)), 'expected', CONCAT('36', CHAR(10 USING utf8mb4)), 'weight', 4, 'public', FALSE, 'comparison', 'EXACT')
));
INSERT INTO cyan_question (category, difficulty, question_type, title, sub_title, answer, initial_code, status, remark)
SELECT 'PYTHON', 1, 'PYTHON', '偶数之和',
  '第一行输入整数 n（1 <= n <= 1000），第二行输入 n 个整数（每个绝对值不超过 10^6）。输出其中所有偶数的和；没有偶数时输出 0。结果末尾换行。示例输入：第一行 5，第二行 1 2 3 4 6；示例输出：12。',
  @case_even, CONCAT('n = int(input())', CHAR(10), 'numbers = list(map(int, input().split()))', CHAR(10), '# 只计算偶数的和', CHAR(10)),
  '0', 'Python练习:20261030:02'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark = 'Python练习:20261030:02');
UPDATE cyan_question SET answer = @case_even WHERE remark = 'Python练习:20261030:02'
  AND JSON_UNQUOTE(JSON_EXTRACT(answer, '$.cases[0].input')) LIKE 'base64:type15:%';

SET @case_words = JSON_OBJECT('cases', JSON_ARRAY(
  JSON_OBJECT('input', CONCAT('apple banana apple pear apple', CHAR(10 USING utf8mb4), 'apple', CHAR(10 USING utf8mb4)), 'expected', CONCAT('3', CHAR(10 USING utf8mb4)), 'weight', 0, 'public', TRUE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('red blue red', CHAR(10 USING utf8mb4), 'green', CHAR(10 USING utf8mb4)), 'expected', CONCAT('0', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('Hi hi Hi', CHAR(10 USING utf8mb4), 'Hi', CHAR(10 USING utf8mb4)), 'expected', CONCAT('2', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('x', CHAR(10 USING utf8mb4), 'x', CHAR(10 USING utf8mb4)), 'expected', CONCAT('1', CHAR(10 USING utf8mb4)), 'weight', 4, 'public', FALSE, 'comparison', 'EXACT')
));
INSERT INTO cyan_question (category, difficulty, question_type, title, sub_title, answer, initial_code, status, remark)
SELECT 'PYTHON', 2, 'PYTHON', '单词出现次数',
  '第一行是一段由单个空格分隔的单词（1 到 1000 个），第二行是要查找的单词。单词只包含大小写英文字母，严格区分大小写。输出该单词在第一行中出现的次数，末尾换行。示例输入：第一行 apple banana apple pear apple，第二行 apple；示例输出：3。',
  @case_words, CONCAT('words = input().split()', CHAR(10), 'target = input().strip()', CHAR(10), '# 输出目标单词出现的次数（区分大小写）', CHAR(10)),
  '0', 'Python练习:20261030:03'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark = 'Python练习:20261030:03');
UPDATE cyan_question SET answer = @case_words WHERE remark = 'Python练习:20261030:03'
  AND JSON_UNQUOTE(JSON_EXTRACT(answer, '$.cases[0].input')) LIKE 'base64:type15:%';

SET @case_run = JSON_OBJECT('cases', JSON_ARRAY(
  JSON_OBJECT('input', CONCAT('7', CHAR(10 USING utf8mb4), '1 2 3 2 4 5 6', CHAR(10 USING utf8mb4)), 'expected', CONCAT('4', CHAR(10 USING utf8mb4)), 'weight', 0, 'public', TRUE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('1', CHAR(10 USING utf8mb4), '42', CHAR(10 USING utf8mb4)), 'expected', CONCAT('1', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('5', CHAR(10 USING utf8mb4), '5 6 6 6 6', CHAR(10 USING utf8mb4)), 'expected', CONCAT('2', CHAR(10 USING utf8mb4)), 'weight', 3, 'public', FALSE, 'comparison', 'EXACT'),
  JSON_OBJECT('input', CONCAT('8', CHAR(10 USING utf8mb4), '-3 -2 0 1 0 1 2 3', CHAR(10 USING utf8mb4)), 'expected', CONCAT('4', CHAR(10 USING utf8mb4)), 'weight', 4, 'public', FALSE, 'comparison', 'EXACT')
));
INSERT INTO cyan_question (category, difficulty, question_type, title, sub_title, answer, initial_code, status, remark)
SELECT 'PYTHON', 3, 'PYTHON', '最长连续严格递增段',
  '第一行输入整数 n（1 <= n <= 1000），第二行输入 n 个整数（每个绝对值不超过 10^6）。求相邻元素严格递增的最长连续段长度；相等不算递增。输出长度，末尾换行。示例输入：第一行 7，第二行 1 2 3 2 4 5 6；示例输出：4。',
  @case_run, CONCAT('n = int(input())', CHAR(10), 'numbers = list(map(int, input().split()))', CHAR(10), '# 求最长连续严格递增段的长度', CHAR(10)),
  '0', 'Python练习:20261030:04'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark = 'Python练习:20261030:04');
UPDATE cyan_question SET answer = @case_run WHERE remark = 'Python练习:20261030:04'
  AND JSON_UNQUOTE(JSON_EXTRACT(answer, '$.cases[0].input')) LIKE 'base64:type15:%';

COMMIT;
