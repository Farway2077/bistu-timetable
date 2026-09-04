package cn.edu.bistu.kebiao.importer

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object BistuPageExtractor {
    const val START_URL =
        "https://jwxt.bistu.edu.cn/jwapp/sys/homeapp/home/index.html?av=&contextPath=/jwapp#/"

    val script: String =
        """
        (() => {
          const clean = (value) => String(value || '')
            .replace(/\u00a0/g, ' ')
            .replace(/[ \t]+/g, ' ')
            .replace(/\n\s+/g, '\n')
            .trim();
          const htmlText = (doc, value) => {
            const holder = doc.createElement('div');
            holder.innerHTML = String(value || '');
            return clean(holder.innerText || holder.textContent || '');
          };
          const visible = (element) => {
            if (!element) return false;
            const style = getComputedStyle(element);
            return style.display !== 'none' && style.visibility !== 'hidden' &&
              element.getClientRects().length > 0;
          };
          const parseWeekday = (value) => {
            const match = clean(value).match(/(?:星期|周)\s*([一二三四五六日天1-7])/);
            if (!match) return 0;
            return ({'一': 1, '二': 2, '三': 3, '四': 4, '五': 5,
              '六': 6, '日': 7, '天': 7})[match[1]] || Number(match[1]) || 0;
          };
          const weekPattern = (value) => {
            const match = clean(value).match(
              /(?:第?\s*)?(?:\d{1,2}\s*[-~—至]\s*\d{1,2}|\d{1,2}(?:\s*[,，、]\s*\d{1,2})+|\d{1,2})\s*周(?:\s*[（(][单双][）)])?/
            );
            return match ? match[0] : '';
          };
          const firstValue = (source, names) => {
            if (!source || typeof source !== 'object') return '';
            const keys = Object.keys(source);
            for (const name of names) {
              const key = keys.find(item => item.toLowerCase() === name.toLowerCase());
              if (key && source[key] !== undefined && source[key] !== null) return source[key];
            }
            return '';
          };
          const labeledValue = (text, labels) => {
            for (const label of labels) {
              const expression = new RegExp('(?:' + label + ')\\s*[:：]\\s*([^\\n，,；;]+)');
              const match = clean(text).match(expression);
              if (match) return clean(match[1]);
            }
            return '';
          };
          const codedRoom = (value) => {
            const match = clean(value).match(
              /(?:^|[^A-Za-z0-9])([A-Za-z]{2,8}\s*[-－—]\s*[A-Za-z]?\d{2,4})(?![A-Za-z0-9])/
            );
            return match ? clean(match[1]).replace(/\s+/g, '') : '';
          };
          const detailTexts = (doc, item) => {
            const result = [];
            const add = (value) => {
              const text = htmlText(doc, value);
              if (text && !result.includes(text)) result.push(text);
            };
            const addDetails = (details) => {
              if (!Array.isArray(details)) return;
              details.forEach(detail => add(
                detail && typeof detail === 'object' ? (detail.text || detail.value) : detail
              ));
            };
            addDetails(item.cellDetail);
            addDetails(item.titleDetail);
            return result;
          };
          const normalizedWeekText = (item, rawText) => {
            const direct = firstValue(item, [
              'weekText', 'weekDescription', 'weekDesc', 'weekString',
              'weeks', 'weekList', 'zcd'
            ]);
            if (Array.isArray(direct) && direct.length) {
              const values = direct.map(value => {
                if (!value || typeof value !== 'object') return value;
                return firstValue(value, ['serialNumber', 'week', 'weekNumber', 'itemCode', 'name']);
              }).filter(value => clean(value));
              if (values.length) return values.join(',') + '周';
            }
            if (typeof direct === 'number') return direct + '周';
            if (clean(direct)) {
              const directText = clean(direct);
              return directText.includes('周') ? directText : directText + '周';
            }
            const start = Number(firstValue(item, ['startWeek', 'beginWeek', 'qsz']));
            const end = Number(firstValue(item, ['endWeek', 'finishWeek', 'jsz']));
            if (start > 0 && end >= start) return start + '-' + end + '周';
            return weekPattern(rawText);
          };
          const normalizeScheduleItem = (doc, item) => {
            const details = detailTexts(doc, item);
            const rawText = clean(details.join('\n'));
            const courseName = htmlText(doc, firstValue(item, [
              'courseName', 'courseTitle', 'kcmc', 'name'
            ])) || details.find(text => !/^(学生组|课程信息|上课信息|教学班)$/.test(text));
            const teacher = htmlText(doc, firstValue(item, [
              'teacherName', 'teacherNames', 'teacher', 'rkjs', 'jsxm'
            ])) || labeledValue(rawText, ['任课教师', '教师', '老师']);
            const room = htmlText(doc, firstValue(item, [
              'classroomName', 'classRoomName', 'classroom', 'roomName',
              'room', 'roomDetail', 'teachingPlace', 'place', 'placeName',
              'classPlace', 'jxcdmc', 'jxcd', 'jasmc', 'cdmc'
            ])) || labeledValue(rawText, ['上课地点', '地点', '教室']) || codedRoom(rawText);
            const startPeriod = Number(firstValue(item, [
              'beginSection', 'startSection', 'beginPeriod', 'startPeriod', 'ksjc'
            ]));
            const endPeriod = Number(firstValue(item, [
              'endSection', 'finishSection', 'endPeriod', 'jsjc'
            ])) || startPeriod;
            return {
              courseName: clean(courseName),
              teacher: clean(teacher),
              room: clean(room),
              weekday: Number(firstValue(item, ['dayOfWeek', 'weekday', 'weekDay', 'xq'])),
              startPeriod,
              endPeriod,
              weekText: normalizedWeekText(item, rawText),
              rawText
            };
          };
          const requestJson = async (url, options) => {
            const response = await fetch(url, Object.assign({
              credentials: 'include',
              cache: 'no-store',
              headers: {'Fetch-Api': 'true'}
            }, options || {}));
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.json();
          };
          const successful = (response) => response && String(response.code) === '0';
          const termCodeOf = (term) => clean(term && (term.itemCode || term.code || term.id));
          const termNameOf = (term) => clean(term && (term.itemName || term.name));
          const normalizedTermText = (value) => clean(value)
            .replace(/\s+/g, '')
            .replace(/[—–－]/g, '-')
            .replace(/第?一学期/g, '1')
            .replace(/第?二学期/g, '2')
            .replace(/第?1学期/g, '1')
            .replace(/第?2学期/g, '2')
            .replace(/第一学期/g, '1')
            .replace(/第二学期/g, '2')
            .replace(/学年/g, '');
          const termAliases = (term) => {
            const name = normalizedTermText(termNameOf(term));
            const code = normalizedTermText(termCodeOf(term));
            const aliases = [name, code].filter(Boolean);
            const match = name.match(/(20\d{2})-(20\d{2})([一二12])/);
            if (match) aliases.push(match[1] + '-' + match[2] + '-' +
              ({'一': '1', '二': '2'}[match[3]] || match[3]));
            return [...new Set(aliases)];
          };
          const resolveSelectedTerm = (doc, terms) => {
            const values = [];
            const add = (value) => {
              const normalized = normalizedTermText(value);
              if (normalized && !values.includes(normalized)) values.push(normalized);
            };
            Array.from(doc.querySelectorAll('select')).filter(visible).forEach(select => {
              Array.from(select.selectedOptions || []).forEach(option => {
                add(option.value);
                add(option.textContent);
              });
              add(select.value);
            });
            Array.from(doc.querySelectorAll(
              '[aria-selected="true"], [aria-current="true"], [role="combobox"], ' +
              'input, [class*="selected"], [class*="Selected"], ' +
              '[class*="selection"], [class*="Selection"]'
            )).filter(visible).forEach(node => {
              add(node.value);
              add(node.textContent);
              add(node.getAttribute('title'));
              add(node.getAttribute('aria-label'));
              add(node.dataset && (node.dataset.termCode || node.dataset.xnxq));
            });
            return terms.map(term => ({
              term,
              score: termAliases(term).reduce((score, alias) => score +
                values.reduce((sum, value) => sum +
                  (value === alias ? 100 : (alias.length >= 6 && value.includes(alias) ? 10 : 0)), 0), 0)
            })).sort((first, second) => second.score - first.score)
              .find(candidate => candidate.score > 0)?.term || null;
          };
          const lessonMatchScore = (pageLessons, apiLessons) => pageLessons.reduce((score, pageLesson) => {
            const pageName = clean(pageLesson.courseName).replace(/\s+/g, '');
            const match = apiLessons.find(apiLesson =>
              clean(apiLesson.courseName).replace(/\s+/g, '') === pageName &&
              (!pageLesson.weekday || apiLesson.weekday === pageLesson.weekday) &&
              (!pageLesson.startPeriod || apiLesson.startPeriod === pageLesson.startPeriod)
            );
            return score + (match ? 1 : 0);
          }, 0);
          const loadTermLessons = async (doc, apiBase, term) => {
            const lessons = [];
            const termCode = termCodeOf(term);
            if (!termCode) return lessons;
            const campusResponse = await requestJson(
              apiBase + 'student/getMyScheduledCampus.do?termCode=' + encodeURIComponent(termCode)
            );
            const campuses = successful(campusResponse) && Array.isArray(campusResponse.datas)
              ? campusResponse.datas : [];
            const campusCodes = campuses.length
              ? campuses.map(item => clean(item && (item.id || item.code))).filter(Boolean)
              : [''];
            for (const campusCode of campusCodes) {
              const form = new URLSearchParams();
              form.set('termCode', termCode);
              form.set('campusCode', campusCode);
              form.set('type', 'term');
              const scheduleResponse = await requestJson(
                apiBase + 'student/getMyScheduleDetail.do',
                {
                  method: 'POST',
                  headers: {
                    'Fetch-Api': 'true',
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                  },
                  body: form.toString()
                }
              );
              if (!successful(scheduleResponse)) continue;
              const data = scheduleResponse.datas || {};
              const arranged = Array.isArray(data.arrangedList)
                ? data.arrangedList : (Array.isArray(data) ? data : []);
              arranged.forEach(item => lessons.push(normalizeScheduleItem(doc, item || {})));
            }
            return lessons;
          };
          const collectApiLessons = async (doc, pageLessons) => {
            const result = {lessons: [], termName: '', warnings: []};
            if (!/\.bistu\.edu\.cn$/i.test(location.hostname) ||
                !location.pathname.includes('/sys/homeapp/')) return result;
            try {
              const contextPath = location.pathname.split('/sys/')[0];
              const apiBase = contextPath + '/sys/homeapp/api/home/';
              const termsResponse = await requestJson(apiBase + 'kb/xnxq.do');
              if (!successful(termsResponse) || !Array.isArray(termsResponse.datas)) {
                throw new Error('无法读取当前学期');
              }
              const terms = termsResponse.datas.filter(Boolean);
              const pageSelectedTerm = resolveSelectedTerm(doc, terms);
              let term = pageSelectedTerm || terms.find(item =>
                item && (item.selected === true || String(item.selected) === '1')
              ) || terms[0];
              if (!termCodeOf(term)) throw new Error('当前学期代码为空');
              let lessons = await loadTermLessons(doc, apiBase, term);

              if (!pageSelectedTerm && pageLessons.length && lessonMatchScore(pageLessons, lessons) === 0) {
                let best = {term, lessons, score: 0};
                const alternatives = await Promise.all(
                  terms.filter(item => item !== term).slice(0, 4).map(async candidate => {
                    const candidateLessons = await loadTermLessons(doc, apiBase, candidate);
                    return {
                      term: candidate,
                      lessons: candidateLessons,
                      score: lessonMatchScore(pageLessons, candidateLessons)
                    };
                  })
                );
                alternatives.forEach(candidate => {
                  if (candidate.score > best.score) best = candidate;
                });
                if (best.score > 0) {
                  term = best.term;
                  lessons = best.lessons;
                }
              }
              result.termName = termNameOf(term);
              result.lessons.push(...lessons);
              if (!result.lessons.length) result.warnings.push('学校接口暂未返回学期课表，已尝试读取页面网格。');
            } catch (error) {
              result.warnings.push('学校课表接口暂时不可用，已尝试读取页面网格。');
            }
            return result;
          };
          const axisOverlap = (firstStart, firstEnd, secondStart, secondEnd) =>
            Math.max(0, Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart));
          const collectGridLessons = (doc) => {
            const lessons = [];
            Array.from(doc.querySelectorAll('[class*="schoolTable___"]')).forEach(table => {
              const dayColumns = Array.from(
                table.querySelectorAll('[class*="kbappTimetableDayColumnRoot___"]')
              );
              const weekdayBackgrounds = Array.from(
                table.querySelectorAll('[class*="weekday___"]')
              ).filter(node => node.querySelector('[class*="sectionBox___"]'));
              const headers = Array.from(table.querySelectorAll('[class*="week___"]'))
                .map(node => parseWeekday(node.innerText))
                .filter(Boolean);
              dayColumns.forEach((column, dayIndex) => {
                const weekday = headers[dayIndex] || dayIndex + 1;
                const background = weekdayBackgrounds[dayIndex];
                const boxes = background ? Array.from(
                  background.querySelectorAll('[class*="sectionBox___"]')
                ) : [];
                const boxRects = boxes.map(box => box.getBoundingClientRect());
                const horizontal = boxRects.length > 1 &&
                  Math.abs(boxRects[boxRects.length - 1].left - boxRects[0].left) >
                  Math.abs(boxRects[boxRects.length - 1].top - boxRects[0].top);
                Array.from(column.querySelectorAll('[id^="course_item_"]')).forEach(card => {
                  const cardRect = card.getBoundingClientRect();
                  const matched = boxRects.map((box, index) => {
                    const overlap = horizontal
                      ? axisOverlap(cardRect.left, cardRect.right, box.left, box.right)
                      : axisOverlap(cardRect.top, cardRect.bottom, box.top, box.bottom);
                    return overlap > 2 ? index : -1;
                  }).filter(index => index >= 0);
                  if (!matched.length) return;
                  const titleWrapper = card.querySelector('.courseItemInfoTextWrapper');
                  const titleNode = titleWrapper && titleWrapper.firstElementChild;
                  const rawText = clean(card.innerText);
                  const courseName = clean(titleNode ? titleNode.innerText : rawText.split('\n')[0]);
                  lessons.push({
                    courseName,
                    teacher: labeledValue(rawText, ['任课教师', '教师', '老师']),
                    room: labeledValue(rawText, ['上课地点', '地点', '教室']) || codedRoom(rawText),
                    weekday,
                    startPeriod: matched[0] + 1,
                    endPeriod: matched[matched.length - 1] + 1,
                    weekText: weekPattern(rawText),
                    rawText
                  });
                });
              });
            });
            return lessons;
          };
          const collect = (doc) => {
            const tables = Array.from(doc.querySelectorAll('table')).slice(0, 20).map(table => {
              const allRows = Array.from(table.querySelectorAll('tr')).slice(0, 80);
              const headers = Array.from(table.querySelectorAll('thead th'))
                .map(cell => clean(cell.innerText)).filter(Boolean);
              const rows = allRows.map(row => Array.from(row.querySelectorAll('th,td'))
                .map(cell => clean(cell.innerText))).filter(row => row.some(Boolean));
              return { headers, rows };
            }).filter(table => table.rows.length > 0);
            const selectors = [
              '[class*="kcb"]',
              '[class*="kbcontent"]', '[class*="schedule"]', '[data-course]'
            ].join(',');
            const dayNames = ['', '一', '二', '三', '四', '五', '六', '日'];
            const withHints = (node) => {
              const text = clean(node.innerText);
              const data = node.dataset || {};
              const day = data.weekday || data.weekDay || data.day || data.xq;
              const start = data.startPeriod || data.startSection || data.start || data.ksjc;
              const end = data.endPeriod || data.endSection || data.end || data.jsjc || start;
              const hints = [];
              if (/^[1-7]$/.test(day || '')) hints.push('星期' + dayNames[Number(day)]);
              if (/^\d{1,2}$/.test(start || '') && /^\d{1,2}$/.test(end || '')) {
                hints.push('第' + start + '-' + end + '节');
              }
              return clean([text, ...hints].filter(Boolean).join('\n'));
            };
            const namedNodes = Array.from(doc.querySelectorAll(selectors));
            const semanticNodes = Array.from(doc.querySelectorAll('td,li,div')).filter(node => {
              if (!visible(node)) return false;
              const text = clean(node.innerText);
              return text.length >= 4 && text.length <= 600 &&
                /\d{1,2}\s*[-~—至]\s*\d{1,2}\s*周/.test(text) &&
                (/\d{1,2}\s*[-~—至]\s*\d{1,2}\s*节/.test(text) || /(?:星期|周)[一二三四五六日天]/.test(text));
            });
            const cards = [...new Set([...namedNodes, ...semanticNodes]
              .map(withHints).filter(text => text.length >= 4))].slice(0, 300);
            return { bodyText: clean(doc.body ? doc.body.innerText : '').slice(0, 50000), tables, cards };
          };
          const documents = [document];
          Array.from(document.querySelectorAll('iframe')).forEach(frame => {
            try { if (frame.contentDocument) documents.push(frame.contentDocument); } catch (_) {}
          });
          const packs = documents.map(collect);
          const gridLessons = documents.flatMap(collectGridLessons);
          const extractionId = (window.__kebiaoExtractionRequestId || 0) + 1;
          window.__kebiaoExtractionRequestId = extractionId;
          window.__kebiaoExtractionResult = null;
          collectApiLessons(document, gridLessons).then(apiResult => {
            if (window.__kebiaoExtractionRequestId !== extractionId) return;
            const hasUsableApiLesson = apiResult.lessons.some(lesson =>
              lesson.courseName && lesson.weekday > 0 && lesson.startPeriod > 0 &&
              lesson.endPeriod >= lesson.startPeriod && lesson.weekText
            );
            const lessons = hasUsableApiLesson ? apiResult.lessons : gridLessons;
            window.__kebiaoExtractionResult = JSON.stringify({
              title: clean(document.title),
              url: location.href,
              bodyText: clean([
                apiResult.termName,
                packs.map(pack => pack.bodyText).filter(Boolean).join('\n\n')
              ].filter(Boolean).join('\n')),
              tables: packs.flatMap(pack => pack.tables).slice(0, 30),
              cards: [...new Set(packs.flatMap(pack => pack.cards))].slice(0, 300),
              lessons,
              extractionWarnings: apiResult.warnings
            });
          }).catch(() => {
            if (window.__kebiaoExtractionRequestId !== extractionId) return;
            window.__kebiaoExtractionResult = JSON.stringify({
              title: clean(document.title),
              url: location.href,
              bodyText: packs.map(pack => pack.bodyText).filter(Boolean).join('\n\n'),
              tables: packs.flatMap(pack => pack.tables).slice(0, 30),
              cards: [...new Set(packs.flatMap(pack => pack.cards))].slice(0, 300),
              lessons: gridLessons,
              extractionWarnings: ['页面结构化课表读取失败，已使用页面网格。']
            });
          });
          return 'pending';
        })();
        """.trimIndent()

    const val resultScript = "window.__kebiaoExtractionResult || null"

    fun decodeJavascriptResult(raw: String): ExtractedPage {
        val first = JSONTokener(raw).nextValue()
        val json = when (first) {
            is JSONObject -> first
            is String -> JSONObject(first)
            else -> error("页面提取结果不是有效对象")
        }
        return ExtractedPage(
            title = json.optString("title"),
            url = json.optString("url"),
            bodyText = json.optString("bodyText"),
            tables = json.optJSONArray("tables").toTables(),
            cards = json.optJSONArray("cards").toStrings(),
            lessons = json.optJSONArray("lessons").toLessons(),
            extractionWarnings = json.optJSONArray("extractionWarnings").toStrings(),
        )
    }

    private fun JSONArray?.toLessons(): List<ExtractedLesson> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val lesson = optJSONObject(index) ?: continue
                add(
                    ExtractedLesson(
                        courseName = lesson.optString("courseName").trim(),
                        teacher = lesson.optString("teacher").trim(),
                        room = lesson.optString("room").trim(),
                        weekday = lesson.optInt("weekday").takeIf { it > 0 },
                        startPeriod = lesson.optInt("startPeriod").takeIf { it > 0 },
                        endPeriod = lesson.optInt("endPeriod").takeIf { it > 0 },
                        weekText = lesson.optString("weekText").trim(),
                        rawText = lesson.optString("rawText").trim(),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toTables(): List<ExtractedTable> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val table = optJSONObject(index) ?: continue
                add(
                    ExtractedTable(
                        headers = table.optJSONArray("headers").toStrings(),
                        rows = table.optJSONArray("rows").toRows(),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toRows(): List<List<String>> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(optJSONArray(index).toStrings())
        }
    }

    private fun JSONArray?.toStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
    }
}
