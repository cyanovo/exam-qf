-- Review before applying to the intended database. No exam or student records are modified.
-- Re-running inserts only missing source markers; disabled questions require teacher review.
-- ROBOT_PRACTICAL: sub_title=task instructions; answer=onsite rubric JSON (not Python test cases).
START TRANSACTION;
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', 'Python 中用于定义代码块的方式是什么？', '[{"label": "A", "content": "使用大括号 {}", "isAnswer": 0}, {"label": "B", "content": "使用缩进", "isAnswer": 1}, {"label": "C", "content": "使用分号 ;", "isAnswer": 0}, {"label": "D", "content": "使用圆括号 ()", "isAnswer": 0}]', 'B', '0', '模拟卷:A:单选:01；来源：A单选1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '以下哪个不是 Python 的合法注释方式？', '[{"label": "A", "content": "#", "isAnswer": 0}, {"label": "B", "content": "''''''", "isAnswer": 0}, {"label": "C", "content": "//", "isAnswer": 1}, {"label": "D", "content": "\\"\\"\\"", "isAnswer": 0}]', 'C', '0', '模拟卷:A:单选:02；来源：A单选2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '下列选项中，哪个是合法的变量名？', '[{"label": "A", "content": "3_data", "isAnswer": 0}, {"label": "B", "content": "_data", "isAnswer": 1}, {"label": "C", "content": "data - 3", "isAnswer": 0}, {"label": "D", "content": "and", "isAnswer": 0}]', 'B', '0', '模拟卷:A:单选:03；来源：A单选3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '使用 input() 函数获取用户输入时，返回的数据类型是什么？', '[{"label": "A", "content": "整型", "isAnswer": 0}, {"label": "B", "content": "浮点型", "isAnswer": 0}, {"label": "C", "content": "字符串", "isAnswer": 1}, {"label": "D", "content": "布尔型", "isAnswer": 0}]', 'C', '0', '模拟卷:A:单选:04；来源：A单选4' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:04；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '哪个运算符可用于计算整数除法？', '[{"label": "A", "content": "/", "isAnswer": 0}, {"label": "B", "content": "//", "isAnswer": 1}, {"label": "C", "content": "%", "isAnswer": 0}, {"label": "D", "content": "*", "isAnswer": 0}]', 'B', '0', '模拟卷:A:单选:05；来源：A单选5' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:05；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '字符串的索引是从哪开始的？', '[{"label": "A", "content": "0", "isAnswer": 1}, {"label": "B", "content": "1", "isAnswer": 0}, {"label": "C", "content": "-1", "isAnswer": 0}, {"label": "D", "content": "-2", "isAnswer": 0}]', 'A', '0', '模拟卷:A:单选:06；来源：A单选6' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:06；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '下列哪个方法可以将字符串转换为小写？', '[{"label": "A", "content": "uppercase()", "isAnswer": 0}, {"label": "B", "content": "capitalize()", "isAnswer": 0}, {"label": "C", "content": "lower()", "isAnswer": 1}, {"label": "D", "content": "swapcase()", "isAnswer": 0}]', 'C', '0', '模拟卷:A:单选:07；来源：A单选7' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:07；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '如何获取字符串的长度？', '[{"label": "A", "content": "len()", "isAnswer": 1}, {"label": "B", "content": "size()", "isAnswer": 0}, {"label": "C", "content": "count()", "isAnswer": 0}, {"label": "D", "content": "length()", "isAnswer": 0}]', 'A', '0', '模拟卷:A:单选:08；来源：A单选8' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:08；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', 'Python 程序的三种控制结构包括：', '[{"label": "A", "content": "顺序、循环、分支", "isAnswer": 0}, {"label": "B", "content": "循环、递归、判断", "isAnswer": 1}, {"label": "C", "content": "递增、递减、顺序", "isAnswer": 0}, {"label": "D", "content": "选择、排序、输出", "isAnswer": 0}]', 'B', '1', '模拟卷:A:单选:09；来源：A单选9；待核对：答案表选B，但顺序/循环/分支对应A' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:09；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '终止循环并跳出的关键字是？', '[{"label": "A", "content": "stop", "isAnswer": 0}, {"label": "B", "content": "exit", "isAnswer": 0}, {"label": "C", "content": "break", "isAnswer": 1}, {"label": "D", "content": "endloop", "isAnswer": 0}]', 'C', '0', '模拟卷:A:单选:10；来源：A单选10' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:10；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', 'Python 中如何定义一个函数？', '[{"label": "A", "content": "def", "isAnswer": 1}, {"label": "B", "content": "function", "isAnswer": 0}, {"label": "C", "content": "define", "isAnswer": 0}, {"label": "D", "content": "func", "isAnswer": 0}]', 'A', '0', '模拟卷:A:单选:11；来源：A单选11' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:11；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '局部变量在 Python 中的作用域是？', '[{"label": "A", "content": "只在定义它们的函数内部有效", "isAnswer": 1}, {"label": "B", "content": "在整个程序中都有效", "isAnswer": 0}, {"label": "C", "content": "在定义它们的文件中有效", "isAnswer": 0}, {"label": "D", "content": "只在主函数内有效", "isAnswer": 0}]', 'A', '0', '模拟卷:A:单选:12；来源：A单选12' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:12；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，如何定义一个列表？', '[{"label": "A", "content": "使用大括号{}定义", "isAnswer": 0}, {"label": "B", "content": "使用尖括号<>定义", "isAnswer": 0}, {"label": "C", "content": "使用方括号[]定义", "isAnswer": 1}, {"label": "D", "content": "使用小括号()定义", "isAnswer": 0}]', 'C', '0', '模拟卷:A:单选:13；来源：A单选13' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:13；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '字典类型的操作方法中，常用于获取所有键的方法是？', '[{"label": "A", "content": "get_keys()", "isAnswer": 0}, {"label": "B", "content": "keys()", "isAnswer": 1}, {"label": "C", "content": "all_keys()", "isAnswer": 0}, {"label": "D", "content": "fetch_keys()", "isAnswer": 0}]', 'B', '0', '模拟卷:A:单选:14；来源：A单选14' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:14；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，如何打开一个文件进行读取操作？', '[{"label": "A", "content": "open(file_name, \\"r\\")", "isAnswer": 1}, {"label": "B", "content": "read(file_name)", "isAnswer": 0}, {"label": "C", "content": "open_file(file_name, mode=\\"read\\")", "isAnswer": 0}, {"label": "D", "content": "read_open(file_name)", "isAnswer": 0}]', 'A', '0', '模拟卷:A:单选:15；来源：A单选15' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:单选:15；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '在 Python 中，使用缩进来定义不同的代码块是必须的。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:A:判断:01；来源：A判断1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:判断:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '在 Python 中，列表和元组都可以随时修改其内容。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 0}, {"label": "B", "content": "错", "isAnswer": 1}]', 'B', '0', '模拟卷:A:判断:02；来源：A判断2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:判断:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', 'while True 可以用来创建无限循环。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:A:判断:03；来源：A判断3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:判断:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '局部变量的作用域仅限于定义它们的函数内部。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:A:判断:04；来源：A判断4' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:判断:04；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '字典类型的键可以是任意不可变的数据类型，例如字符串、整数或元组。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:A:判断:05；来源：A判断5' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:判断:05；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 1, 'ROBOT_PRACTICAL', '热身准备任务', '1.（简单题，满分15分） 请编写一段代码完成“热身准备任务”：
(1)将机器人的音量设置为 60。（3分）
(2)语音播报“开始热身，举起右手”。（3分）
(3)调用API，控制机器人的1号舵机转动到 180 度。（3分）
(4)语音播报“热身动作完成”。（3分）
(5) 导入time模块，并等待 2 秒（time.sleep(2)）。（3分）', '[{"title": "将机器人的音量设置为 60。", "maxScore": 3}, {"title": "语音播报“开始热身，举起右手”。", "maxScore": 3}, {"title": "调用API，控制机器人的1号舵机转动到 180 度。", "maxScore": 3}, {"title": "语音播报“热身动作完成”。", "maxScore": 3}, {"title": "导入time模块，并等待 2 秒（time.sleep(2)）。", "maxScore": 3}]', '0', '模拟卷:A:实操:01；来源：A实操1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:实操:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 3, 'ROBOT_PRACTICAL', '电量巡航任务', '2.（中等题，满分20分） 请编写一段代码完成“电量巡航任务”：
(1)设定一个无限循环（while True），并导入time模块。（4分）
(2)每次循环开始时读取机器人的电量数值。（4分）
(3)编写if...else判断结构：如果获取到的电量大于 20，语音播报“电量充足”，并将胸口灯效（type设为"button"）设置为绿色（"green"），模式为常亮（"solid"），然后退出整个循环（使用 break）。（4分）
(4) 如果电量小于或等于 20，语音播报“需要充电”。（4分）
(5) 在电量不足分支内，将胸口灯效设置为红色（"red"），模式为闪烁（"blink"），并让程序睡眠等待 5 秒。（4分）', '[{"title": "设定一个无限循环（while True），并导入time模块。", "maxScore": 4}, {"title": "每次循环开始时读取机器人的电量数值。", "maxScore": 4}, {"title": "编写if...else判断结构：如果获取到的电量大于 20，语音播报“电量充足”，并将胸口灯效（type设为\\"button\\"）设置为绿色（\\"green\\"），模式为常亮（\\"solid\\"），然后退出整个循环（使用 break）。", "maxScore": 4}, {"title": "如果电量小于或等于 20，语音播报“需要充电”。", "maxScore": 4}, {"title": "在电量不足分支内，将胸口灯效设置为红色（\\"red\\"），模式为闪烁（\\"blink\\"），并让程序睡眠等待 5 秒。", "maxScore": 4}]', '0', '模拟卷:A:实操:02；来源：A实操2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:实操:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 5, 'ROBOT_PRACTICAL', '才艺展示任务', '3.（困难题，满分25分） 请编写一段代码完成“才艺展示任务”：
(1)语音播报“请下达才艺指令”。（3分）
(2)执行一次语义理解提取结果（使用 sync_do_voice_asr_value() ），将获取到的文本保存在 asr_result 变量中。（4分）
(3)编写if...elif...else结构对文本内容进行多分支判断。（4分）
(4)如果指令包含“唱歌”，则异步播放音乐“我和我的祖国.mp3”。（4分）
(5)如果指令包含“俯卧撑”，则异步执行动作“PushUp”。（4分）
(6) 如果指令包含“站起来”，则异步执行动作“GetUp”。（3分）
(7) 其他情况，播报“该才艺尚未学习”，并在所有条件判断完全结束后播报“才艺展示结束”。（3分）', '[{"title": "语音播报“请下达才艺指令”。", "maxScore": 3}, {"title": "执行一次语义理解提取结果（使用 sync_do_voice_asr_value() ），将获取到的文本保存在 asr_result 变量中。", "maxScore": 4}, {"title": "编写if...elif...else结构对文本内容进行多分支判断。", "maxScore": 4}, {"title": "如果指令包含“唱歌”，则异步播放音乐“我和我的祖国.mp3”。", "maxScore": 4}, {"title": "如果指令包含“俯卧撑”，则异步执行动作“PushUp”。", "maxScore": 4}, {"title": "如果指令包含“站起来”，则异步执行动作“GetUp”。", "maxScore": 3}, {"title": "其他情况，播报“该才艺尚未学习”，并在所有条件判断完全结束后播报“才艺展示结束”。", "maxScore": 3}]', '0', '模拟卷:A:实操:03；来源：A实操3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:A:实操:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '以下关于 Python 数据类型的描述，错误的是？', '[{"label": "A", "content": "字符串属于不可变数据类型", "isAnswer": 0}, {"label": "B", "content": "列表属于可变数据类型", "isAnswer": 0}, {"label": "C", "content": "数字类型包括整数和字符串", "isAnswer": 1}, {"label": "D", "content": "元组属于不可变数据类型", "isAnswer": 0}]', 'C', '0', '模拟卷:B:单选:01；来源：B单选1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中赋值语句正确的是？', '[{"label": "A", "content": "x + 1 = y", "isAnswer": 0}, {"label": "B", "content": "y = x + 1", "isAnswer": 1}, {"label": "C", "content": "1 = x", "isAnswer": 0}, {"label": "D", "content": "x += 1 = y", "isAnswer": 0}]', 'B', '0', '模拟卷:B:单选:02；来源：B单选2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '以下哪个不是 Python 的数字类型？', '[{"label": "A", "content": "列表类型", "isAnswer": 1}, {"label": "B", "content": "整数类型", "isAnswer": 0}, {"label": "C", "content": "浮点数类型", "isAnswer": 0}, {"label": "D", "content": "复数类型", "isAnswer": 0}]', 'A', '0', '模拟卷:B:单选:03；来源：B单选3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', 'Python 中用于取绝对值的函数是？', '[{"label": "A", "content": "ceil()", "isAnswer": 0}, {"label": "B", "content": "abs()", "isAnswer": 1}, {"label": "C", "content": "round()", "isAnswer": 0}, {"label": "D", "content": "sqrt()", "isAnswer": 0}]', 'B', '0', '模拟卷:B:单选:04；来源：B单选4' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:04；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '如何使用 Python 进行字符串切片？', '[{"label": "A", "content": "使用 substring()", "isAnswer": 0}, {"label": "B", "content": "使用 split()", "isAnswer": 0}, {"label": "C", "content": "使用 join()", "isAnswer": 0}, {"label": "D", "content": "使用 []", "isAnswer": 1}]', 'D', '0', '模拟卷:B:单选:05；来源：B单选5' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:05；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，哪个函数可以检查变量的类型？', '[{"label": "A", "content": "datatype()", "isAnswer": 0}, {"label": "B", "content": "typeof()", "isAnswer": 0}, {"label": "C", "content": "type()", "isAnswer": 1}, {"label": "D", "content": "checktype()", "isAnswer": 0}]', 'C', '0', '模拟卷:B:单选:06；来源：B单选6' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:06；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '哪个 Python 方法可以删除字符串末尾的空格？', '[{"label": "A", "content": "trim()", "isAnswer": 0}, {"label": "B", "content": "lstrip()", "isAnswer": 0}, {"label": "C", "content": "rstrip()", "isAnswer": 1}, {"label": "D", "content": "strip()", "isAnswer": 0}]', 'C', '0', '模拟卷:B:单选:07；来源：B单选7' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:07；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '二分支结构在 Python 中通常使用的关键字是？', '[{"label": "A", "content": "then", "isAnswer": 0}, {"label": "B", "content": "elseif", "isAnswer": 0}, {"label": "C", "content": "else", "isAnswer": 1}, {"label": "D", "content": "elif", "isAnswer": 0}]', 'C', '0', '模拟卷:B:单选:08；来源：B单选8' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:08；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '现在 Python 中，无限循环通常使用什么来实现？', '[{"label": "A", "content": "for 循环", "isAnswer": 0}, {"label": "B", "content": "while True 循环", "isAnswer": 0}, {"label": "C", "content": "do - while 循环", "isAnswer": 0}, {"label": "D", "content": "until 循环", "isAnswer": 1}]', 'D', '1', '模拟卷:B:单选:09；来源：B单选9；待核对：答案表选D，但无限循环应选B' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:09；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '异常处理机制在 Python 中通过哪种方式实现？', '[{"label": "A", "content": "try - exit", "isAnswer": 0}, {"label": "B", "content": "try - finally", "isAnswer": 0}, {"label": "C", "content": "try - catch", "isAnswer": 0}, {"label": "D", "content": "try - except", "isAnswer": 1}]', 'D', '0', '模拟卷:B:单选:10；来源：B单选10' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:10；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在函数内部修改全局变量的值，应该使用哪个关键字？', '[{"label": "A", "content": "local", "isAnswer": 0}, {"label": "B", "content": "global", "isAnswer": 1}, {"label": "C", "content": "modify", "isAnswer": 0}, {"label": "D", "content": "change", "isAnswer": 0}]', 'B', '0', '模拟卷:B:单选:11；来源：B单选11' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:11；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '关于组合数据类型的基本概念，以下描述正确的是？', '[{"label": "A", "content": "组合数据类型只包括列表和元组", "isAnswer": 0}, {"label": "B", "content": "索引用于访问序列中的单个元素", "isAnswer": 1}, {"label": "C", "content": "切片操作仅适用于元组类型", "isAnswer": 0}, {"label": "D", "content": "列表类型不支持索引操作", "isAnswer": 0}]', 'B', '0', '模拟卷:B:单选:12；来源：B单选12' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:12；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '如何向列表末尾添加一个新元素？', '[{"label": "A", "content": "使用 add() 方法", "isAnswer": 0}, {"label": "B", "content": "使用 push() 方法", "isAnswer": 0}, {"label": "C", "content": "使用 insert() 方法", "isAnswer": 0}, {"label": "D", "content": "使用 append() 方法", "isAnswer": 1}]', 'D', '0', '模拟卷:B:单选:13；来源：B单选13' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:13；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '如何表示和处理一维数据？', '[{"label": "A", "content": "使用列表", "isAnswer": 1}, {"label": "B", "content": "使用字典", "isAnswer": 0}, {"label": "C", "content": "使用集合", "isAnswer": 0}, {"label": "D", "content": "使用元组", "isAnswer": 0}]', 'A', '0', '模拟卷:B:单选:14；来源：B单选14' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:14；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '确定关闭一个已打开的文件，应该使用？', '[{"label": "A", "content": "file.close()", "isAnswer": 1}, {"label": "B", "content": "close_file(file)", "isAnswer": 0}, {"label": "C", "content": "end(file)", "isAnswer": 0}, {"label": "D", "content": "file.stop()", "isAnswer": 0}]', 'A', '0', '模拟卷:B:单选:15；来源：B单选15' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:单选:15；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', 'Python 中 if 和 for 可以作为变量名。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 0}, {"label": "B", "content": "错", "isAnswer": 1}]', 'B', '0', '模拟卷:B:判断:01；来源：B判断1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:判断:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', 'int("3.14") 是合法的 Python 表达式。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 0}, {"label": "B", "content": "错", "isAnswer": 1}]', 'B', '0', '模拟卷:B:判断:02；来源：B判断2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:判断:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', 'break 关键字主要用于终止循环，并跳出当前循环的执行。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:B:判断:03；来源：B判断3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:判断:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '参数名称传递可以帮助避免因参数顺序混乱而引起的错误。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:B:判断:04；来源：B判断4' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:判断:04；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '在 Python 中，列表属于有序集合。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:B:判断:05；来源：B判断5' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:判断:05；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 1, 'ROBOT_PRACTICAL', '舵机状态自检任务', '（简单题，满分15分） 请编写一段代码完成“舵机状态自检任务”：
(1)语音播报“开始检测左臂角度”。（3分）
(2)获取机器人2号舵机（左手）的当前角度值，并保存在变量 angle 中。（3分）
(3)编写if...else判断：如果角度等于 0，播报“角度正常”。（3分）
(4)如果角度不等于 0，则控制2号舵机恢复到 0 度。（3分）
(5) 无论是否调整，最后都播报“单项检测完毕”。（3分）', '[{"title": "语音播报“开始检测左臂角度”。", "maxScore": 3}, {"title": "获取机器人2号舵机（左手）的当前角度值，并保存在变量 angle 中。", "maxScore": 3}, {"title": "编写if...else判断：如果角度等于 0，播报“角度正常”。", "maxScore": 3}, {"title": "如果角度不等于 0，则控制2号舵机恢复到 0 度。", "maxScore": 3}, {"title": "无论是否调整，最后都播报“单项检测完毕”。", "maxScore": 3}]', '0', '模拟卷:B:实操:01；来源：B实操1, 原第三套实操1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:实操:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 3, 'ROBOT_PRACTICAL', '颜色指令运动任务', '（中等题，满分20分） 请编写一段代码完成“颜色指令运动任务”：
(1)使用 for 循环检测 3 次，每次循环导入并使用 time 模块延迟 2 秒。（4分）
(2)每次循环开始时，播报“请出示颜色指令”，并执行一次同步颜色识别（ YanAPI.sync_do_color_recognition() ）。（4分）
(3)假设返回的字典中包含了键名为 "color" 的结果，提取该结果。（4分）
(4)编写if...elif...else结构：如果颜色是 "red"，执行动作 "Stop"。如果颜色是 "green"，执行动作 "Forward"。（4分）
(5) 如果是其他颜色，播报“无效颜色”。（4分）', '[{"title": "使用 for 循环检测 3 次，每次循环导入并使用 time 模块延迟 2 秒。", "maxScore": 4}, {"title": "每次循环开始时，播报“请出示颜色指令”，并执行一次同步颜色识别（ YanAPI.sync_do_color_recognition() ）。", "maxScore": 4}, {"title": "假设返回的字典中包含了键名为 \\"color\\" 的结果，提取该结果。", "maxScore": 4}, {"title": "编写if...elif...else结构：如果颜色是 \\"red\\"，执行动作 \\"Stop\\"。如果颜色是 \\"green\\"，执行动作 \\"Forward\\"。", "maxScore": 4}, {"title": "如果是其他颜色，播报“无效颜色”。", "maxScore": 4}]', '0', '模拟卷:B:实操:02；来源：B实操2, 原第三套实操2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:实操:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 5, 'ROBOT_PRACTICAL', '性别识别与互动任务', '（困难题，满分25分） 请编写一段代码完成“性别识别与互动任务”：
(1)语音播报“请看着我的眼睛，准备识别”。（3分）
(2)执行同步人脸识别接口提取性别数值（ YanAPI.sync_do_face_recognition_value(type="gender") ），将结果保存在变量 gender 中。（4分）
(3)编写if...elif...else结构对性别进行判断。（4分）
(4)如果性别为 "male"（男），将音量设置为 80，并播放动作 "Fight_RHit"。（4分）
(5)如果性别为 "female"（女），将音量设置为 60，并播放动作 "Hug"。（4分）
(6)如果未识别到有效性别，播报“未识别到有效面部”。（3分）
(7) 不论识别结果为男还是女（提示：将这段代码放在对应的分支结束之后），统一在最后播放音乐 "WeAreTakingOff.mp3"。（3分）', '[{"title": "语音播报“请看着我的眼睛，准备识别”。", "maxScore": 3}, {"title": "执行同步人脸识别接口提取性别数值（ YanAPI.sync_do_face_recognition_value(type=\\"gender\\") ），将结果保存在变量 gender 中。", "maxScore": 4}, {"title": "编写if...elif...else结构对性别进行判断。", "maxScore": 4}, {"title": "如果性别为 \\"male\\"（男），将音量设置为 80，并播放动作 \\"Fight_RHit\\"。", "maxScore": 4}, {"title": "如果性别为 \\"female\\"（女），将音量设置为 60，并播放动作 \\"Hug\\"。", "maxScore": 4}, {"title": "如果未识别到有效性别，播报“未识别到有效面部”。", "maxScore": 3}, {"title": "不论识别结果为男还是女（提示：将这段代码放在对应的分支结束之后），统一在最后播放音乐 \\"WeAreTakingOff.mp3\\"。", "maxScore": 3}]', '0', '模拟卷:B:实操:03；来源：B实操3, 原第三套实操3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:B:实操:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', 'Python 中，浮点数可以被表示为？', '[{"label": "A", "content": "3.14e-2", "isAnswer": 1}, {"label": "B", "content": "2.71828", "isAnswer": 0}, {"label": "C", "content": "100", "isAnswer": 0}, {"label": "D", "content": "''3.14''", "isAnswer": 0}]', 'A', '1', '模拟卷:C:单选:01；来源：C单选1, 原第三套单选1；待核对：A与B均是浮点数表示，单选不唯一' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，哪个方法可以用于字符串格式化？', '[{"label": "A", "content": "replace()", "isAnswer": 0}, {"label": "B", "content": "format()", "isAnswer": 1}, {"label": "C", "content": "concat()", "isAnswer": 0}, {"label": "D", "content": "slice()", "isAnswer": 0}]', 'B', '0', '模拟卷:C:单选:02；来源：C单选2, 原第三套单选2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '关于类型判断和类型转换，以下说法正确的是？', '[{"label": "A", "content": "类型判断可以通过 type() 函数实现", "isAnswer": 1}, {"label": "B", "content": "可以使用 int() 进行字符串到浮点数的转换", "isAnswer": 0}, {"label": "C", "content": "字符串类型可以直接与整数类型进行运算", "isAnswer": 0}, {"label": "D", "content": "类型转换时会自动进行取整操作", "isAnswer": 0}]', 'A', '0', '模拟卷:C:单选:03；来源：C单选3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '如何将一个整数转换为字符串类型？', '[{"label": "A", "content": "to_string()", "isAnswer": 0}, {"label": "B", "content": "str()", "isAnswer": 1}, {"label": "C", "content": "convert_to_string()", "isAnswer": 0}, {"label": "D", "content": "stringify()", "isAnswer": 0}]', 'B', '0', '模拟卷:C:单选:04；来源：C单选4, 原第三套单选4' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:04；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，多分支结构的实现通常是通过？', '[{"label": "A", "content": "嵌套的 if - else 语句", "isAnswer": 1}, {"label": "B", "content": "switch - case 结构", "isAnswer": 0}, {"label": "C", "content": "for 循环", "isAnswer": 0}, {"label": "D", "content": "while 循环", "isAnswer": 0}]', 'A', '0', '模拟卷:C:单选:05；来源：C单选5, 原第三套单选5' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:05；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在循环中直接跳转到下一个循环迭代？', '[{"label": "A", "content": "skip", "isAnswer": 0}, {"label": "B", "content": "pass", "isAnswer": 0}, {"label": "C", "content": "continue", "isAnswer": 1}, {"label": "D", "content": "next", "isAnswer": 0}]', 'C', '0', '模拟卷:C:单选:06；来源：C单选6, 原第三套单选6' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:06；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，哪个控制结构用于重复执行一段代码？', '[{"label": "A", "content": "分支结构", "isAnswer": 0}, {"label": "B", "content": "异常处理结构", "isAnswer": 0}, {"label": "C", "content": "循环结构", "isAnswer": 1}, {"label": "D", "content": "顺序结构", "isAnswer": 0}]', 'C', '0', '模拟卷:C:单选:07；来源：C单选7, 原第三套单选7' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:07；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，函数的返回值由什么决定？', '[{"label": "A", "content": "函数内的循环次数", "isAnswer": 0}, {"label": "B", "content": "函数内的条件判断", "isAnswer": 0}, {"label": "C", "content": "函数定义时的返回语句", "isAnswer": 1}, {"label": "D", "content": "函数名称", "isAnswer": 0}]', 'C', '0', '模拟卷:C:单选:08；来源：C单选8, 原第三套单选8' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:08；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '当需要定义一个接收可变数量参数的函数时，应该使用哪种参数形式？', '[{"label": "A", "content": "默认参数", "isAnswer": 0}, {"label": "B", "content": "可变位置参数 *args", "isAnswer": 1}, {"label": "C", "content": "关键字参数", "isAnswer": 0}, {"label": "D", "content": "可变关键字参数 **kwargs", "isAnswer": 0}]', 'B', '0', '模拟卷:C:单选:09；来源：C单选9, 原第三套单选9' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:09；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '下列哪个不是列表类型常用的操作函数？', '[{"label": "A", "content": "append()", "isAnswer": 0}, {"label": "B", "content": "insert()", "isAnswer": 0}, {"label": "C", "content": "update()", "isAnswer": 1}, {"label": "D", "content": "remove()", "isAnswer": 0}]', 'C', '0', '模拟卷:C:单选:10；来源：C单选10, 原第三套单选10' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:10；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '关于字典类型的定义索引，以下说法正确的是？', '[{"label": "A", "content": "字典类型可以通过键来索引对应的值", "isAnswer": 1}, {"label": "B", "content": "字典类型只能通过位置索引", "isAnswer": 0}, {"label": "C", "content": "字典类型无法存储多个键-值对", "isAnswer": 0}, {"label": "D", "content": "字典类型只能存储整数类型的键", "isAnswer": 0}]', 'A', '0', '模拟卷:C:单选:11；来源：C单选11, 原第三套单选11' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:11；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '下列哪个不是字典类型常见的操作函数？', '[{"label": "A", "content": "keys()", "isAnswer": 0}, {"label": "B", "content": "values()", "isAnswer": 0}, {"label": "C", "content": "items()", "isAnswer": 0}, {"label": "D", "content": "append()", "isAnswer": 1}]', 'D', '0', '模拟卷:C:单选:12；来源：C单选12, 原第三套单选12' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:12；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '二维数据的处理方式通常涉及？', '[{"label": "A", "content": "使用循环嵌套", "isAnswer": 1}, {"label": "B", "content": "使用递归函数", "isAnswer": 0}, {"label": "C", "content": "使用条件语句", "isAnswer": 0}, {"label": "D", "content": "使用异常处理", "isAnswer": 0}]', 'A', '0', '模拟卷:C:单选:13；来源：C单选13, 原第三套单选13' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:13；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '为了在 Python 中正确对一维和二维数据文件进行读写操作，CSV是指？', '[{"label": "A", "content": "Comma-Separated Variables", "isAnswer": 0}, {"label": "B", "content": "Centralized Standard Values", "isAnswer": 0}, {"label": "C", "content": "Comprehensive System Verification", "isAnswer": 0}, {"label": "D", "content": "Concatenated Statistical Values", "isAnswer": 1}]', 'D', '1', '模拟卷:C:单选:14；来源：C单选14, 原第三套单选14；待核对：CSV的标准英文为Comma-Separated Values，四项均不准确' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:14；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '在 Python 中，如何确定一个文件是否已经关闭？', '[{"label": "A", "content": "file.is_closed()", "isAnswer": 1}, {"label": "B", "content": "closed(file)", "isAnswer": 0}, {"label": "C", "content": "检查文件对象的状态", "isAnswer": 0}, {"label": "D", "content": "file.is_closed", "isAnswer": 0}]', 'A', '1', '模拟卷:C:单选:15；来源：C单选15, 原第三套单选15；待核对：文件关闭状态应检查file.closed，四项均不准确' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:单选:15；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', 'Python 允许使用多种编程范式，包括面向对象和命令式编程。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:C:判断:01；来源：C判断1, 原第三套判断1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:判断:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '"Hello".upper() 将返回字符串"hello"。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 0}, {"label": "B", "content": "错", "isAnswer": 1}]', 'B', '0', '模拟卷:C:判断:02；来源：C判断2, 原第三套判断2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:判断:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '在 Python 中，使用 if-else 构成的分支结构只有一条分支。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 0}, {"label": "B", "content": "错", "isAnswer": 1}]', 'B', '0', '模拟卷:C:判断:03；来源：C判断3, 原第三套判断3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:判断:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', '默认参数传递可以使函数调用时部分参数变得非必需。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:C:判断:04；来源：C判断4, 原第三套判断4' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:判断:04；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'JUDGE', 'Python 中的字符串可以使用索引来访问单个字符。 （ ）', '[{"label": "A", "content": "对", "isAnswer": 1}, {"label": "B", "content": "错", "isAnswer": 0}]', 'A', '0', '模拟卷:C:判断:05；来源：C判断5, 原第三套判断5' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:判断:05；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 1, 'ROBOT_PRACTICAL', '灯效与声音初始化', '（简单题，满分15分） 请编写一段代码完成“灯效与声音初始化”：
(1) 将机器人的音量设置为 100。（3分）
(2) 获取当前机器人的电量并保存在变量中。（4分）
(3) 将胸口灯效（type设为"button"）设置为蓝色（"blue"），模式为呼吸（"breath"）。（4分）
(4) 语音播报“系统启动完毕，开始运行”。（4分）', '[{"title": "将机器人的音量设置为 100。", "maxScore": 3}, {"title": "获取当前机器人的电量并保存在变量中。", "maxScore": 4}, {"title": "将胸口灯效（type设为\\"button\\"）设置为蓝色（\\"blue\\"），模式为呼吸（\\"breath\\"）。", "maxScore": 4}, {"title": "语音播报“系统启动完毕，开始运行”。", "maxScore": 4}]', '0', '模拟卷:C:实操:01；来源：C实操1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:实操:01；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 3, 'ROBOT_PRACTICAL', '手势控制动作任务', '（中等题，满分20分） 请编写一段代码完成“手势控制动作任务”：
(1) 使用 for 循环执行 4 次，每次循环开头使用 time.sleep(2) 暂停 2 秒。（4分）
(2) 语音播报“请出示手势”，并调用同步手势识别接口（ YanAPI.sync_do_gesture_recognition() ）。（4分）
(3) 假设返回的字典中包含手势结果的键名为 "gesture"，提取该手势结果。（4分）
(4) 编写if...elif...else结构：如果手势是 "ok"，则执行动作 "PushUp"。（4分）
(5) 如果手势是 "stone"（石头），则执行动作 "Fight_RHit"；其他情况播报“手势无法识别”。（4分）', '[{"title": "使用 for 循环执行 4 次，每次循环开头使用 time.sleep(2) 暂停 2 秒。", "maxScore": 4}, {"title": "语音播报“请出示手势”，并调用同步手势识别接口（ YanAPI.sync_do_gesture_recognition() ）。", "maxScore": 4}, {"title": "假设返回的字典中包含手势结果的键名为 \\"gesture\\"，提取该手势结果。", "maxScore": 4}, {"title": "编写if...elif...else结构：如果手势是 \\"ok\\"，则执行动作 \\"PushUp\\"。", "maxScore": 4}, {"title": "如果手势是 \\"stone\\"（石头），则执行动作 \\"Fight_RHit\\"；其他情况播报“手势无法识别”。", "maxScore": 4}]', '0', '模拟卷:C:实操:02；来源：C实操2' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:实操:02；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 5, 'ROBOT_PRACTICAL', '核心动作自检任务', '（困难题，满分25分） 请编写一段代码完成“核心动作自检任务”：
(1) 语音播报“开始检测核心动作库”。（4分）
(2) 获取机器人内部所有的动作列表数据（调用 YanAPI.get_motion_list_value() ），并存入变量 motion_list 中。（5分）
(3) 编写 if...else 判断：使用 in 关键字，直接检查英文动作名 "PushUp" 是否存在于 motion_list 列表中。（6分）
(4) 如果存在，播报“动作完好，立刻执行”，并调用异步播放动作接口执行 "PushUp" 动作。（5分）
(5) 如果不存在，播报“系统文件丢失”，并将胸口灯效（"button"）设置为红色（"red"）闪烁（"blink"）。（5分）', '[{"title": "语音播报“开始检测核心动作库”。", "maxScore": 4}, {"title": "获取机器人内部所有的动作列表数据（调用 YanAPI.get_motion_list_value() ），并存入变量 motion_list 中。", "maxScore": 5}, {"title": "编写 if...else 判断：使用 in 关键字，直接检查英文动作名 \\"PushUp\\" 是否存在于 motion_list 列表中。", "maxScore": 6}, {"title": "如果存在，播报“动作完好，立刻执行”，并调用异步播放动作接口执行 \\"PushUp\\" 动作。", "maxScore": 5}, {"title": "如果不存在，播报“系统文件丢失”，并将胸口灯效（\\"button\\"）设置为红色（\\"red\\"）闪烁（\\"blink\\"）。", "maxScore": 5}]', '0', '模拟卷:C:实操:03；来源：C实操3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:C:实操:03；%');
INSERT INTO cyan_question(category,difficulty,question_type,title,sub_title,answer,status,remark)
SELECT 'PYTHON', 2, 'SINGLE_CHOICE', '关于类型判断和类型转换，以下说法正确的是？', '[{"label": "A", "content": "可以使用 int() 进行字符串到整数的转换", "isAnswer": 1}, {"label": "B", "content": "类型判断可以通过 type() 函数实现", "isAnswer": 0}, {"label": "C", "content": "字符串类型可以直接与整数类型进行运算", "isAnswer": 0}, {"label": "D", "content": "类型转换时会自动进行取整操作", "isAnswer": 0}]', 'A', '0', '模拟卷:原第三套:单选:03；来源：原第三套单选3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cyan_question WHERE remark LIKE '模拟卷:原第三套:单选:03；%');
COMMIT;
