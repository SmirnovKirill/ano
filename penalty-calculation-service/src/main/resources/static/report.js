const tabs = [...document.querySelectorAll('[role="tab"]')];
const template = document.querySelector('#report-form');
const reportForm = document.querySelector('#reports-form');
const contractNumber = document.querySelector('#contract-number');
const stageNumber = document.querySelector('#stage-number');
const submissionDates = document.querySelector('.submission-dates');
const calculationInputs = document.querySelector('.calculation-inputs');
const stageAmount = calculationInputs.querySelector('[name="stageAmount"]');
const excludedDateRows = document.querySelector('#excluded-date-rows');
const result = document.querySelector('#result');
const calculationResult = document.querySelector('#calculation-result');
const submit = document.querySelector('#submit-report');
const calculationDate = document.querySelector('#calculationDate');
const plannedDateHint = template.querySelector('[data-planned-date]').textContent;
function today() {
  const date = new Date();
  return `${String(date.getDate()).padStart(2, '0')}.${String(date.getMonth() + 1).padStart(2, '0')}.${date.getFullYear()}`;
}
calculationDate.value = today();
new ResizeObserver(() => document.documentElement.style.setProperty('--actions-height', `${document.querySelector('.actions').offsetHeight}px`))
  .observe(document.querySelector('.actions'));

function parseDate(value) {
  if (!/^\d{2}\.\d{2}\.\d{4}$/.test(value)) return null;
  const [day, month, year] = value.split('.').map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day ? date : null;
}

function dateError(value) {
  if (!value) return 'Укажите дату.';
  const date = parseDate(value);
  if (!date) return 'Введите корректную дату в формате дд.мм.гггг.';
  return date > new Date() ? 'Дата не может быть в будущем.' : '';
}

const panels = tabs.map((tab, index) => {
  const panel = index ? template.cloneNode(true) : template;
  panel.id = tab.getAttribute('aria-controls');
  panel.dataset.report = tab.dataset.report;
  panel.setAttribute('aria-labelledby', tab.id);
  panel.hidden = index !== 0;
  if (index) {
    const prefix = `${tab.dataset.report}-`;
    panel.querySelectorAll('[id]').forEach(element => element.id = prefix + element.id);
    panel.querySelectorAll('[aria-labelledby]').forEach(element => {
      element.setAttribute('aria-labelledby', prefix + element.getAttribute('aria-labelledby'));
    });
    template.parentElement.append(panel);
  }
  return panel;
});

tabs.forEach((tab, index) => tab.addEventListener('click', () => {
  tabs.forEach((button, i) => {
    button.setAttribute('aria-selected', String(i === index));
    button.tabIndex = i === index ? 0 : -1;
    panels[i].hidden = i !== index;
  });
}));

document.querySelector('.tabs').addEventListener('keydown', event => {
  const index = tabs.indexOf(event.target);
  const next = { ArrowRight: (index + 1) % 3, ArrowLeft: (index + 2) % 3, Home: 0, End: 2 }[event.key];
  if (next === undefined) return;
  event.preventDefault();
  tabs[next].click();
  tabs[next].focus();
});

reportForm.addEventListener('input', ({ target }) => {
  if (!target.matches('input')) return;
  if (target === contractNumber) target.value = target.value.replace(/\D/g, '').slice(0, 2);
  if (target === stageNumber) target.value = target.value.replace(/\D/g, '');
  if (target === stageAmount) formatAmount(target);
  if (target.matches('[data-date]')) {
    const beforeCaret = target.value.slice(0, target.selectionStart).replace(/\D/g, '').length;
    target.value = target.value.replace(/\D/g, '').slice(0, 8)
      .replace(/^(\d{2})(\d)/, '$1.$2').replace(/^(\d{2}\.\d{2})(\d)/, '$1.$2');
    const caret = beforeCaret + (beforeCaret > 2) + (beforeCaret > 4);
    target.setSelectionRange(caret, caret);
    if (target.matches('[data-field="returnedForRevisionDate"]')) {
      updatePanelPlannedDates(target.closest('[role="tabpanel"]'));
    }
  }
  validateForm();
});

function formatAmount(input) {
  let value = input.value.replace(/[^\d.,]/g, '');
  const separator = value.search(/[.,]/);
  if (separator !== -1) value = value.slice(0, separator + 1) + value.slice(separator + 1).replace(/[.,]/g, '').slice(0, 2);
  let remaining = input.value.slice(0, input.selectionStart).replace(/[^\d.,]/g, '').length;
  input.value = value.replace(/^\d+/, digits => digits.replace(/\B(?=(\d{3})+(?!\d))/g, ' '));
  let caret = 0;
  while (remaining > 0 && caret < input.value.length) {
    if (input.value[caret] !== ' ') remaining--;
    caret++;
  }
  input.setSelectionRange(caret, caret);
}

reportForm.addEventListener('change', ({ target }) => {
  if (!target.matches('[data-field="remarksCorrected"]')) return;
  const panel = target.closest('[role="tabpanel"]');
  updateCaseNumbers(panel);
  updatePanelPlannedDates(panel);
  validateForm();
});

document.querySelector('#add-excluded-date').addEventListener('click', () => {
  addExcludedDate().querySelector('input').focus();
});

function addExcludedDate(value = '') {
  excludedDateRows.append(document.querySelector('#excluded-date-template').content.cloneNode(true));
  const row = excludedDateRows.lastElementChild;
  row.querySelector('input').value = value;
  return row;
}

excludedDateRows.addEventListener('click', ({ target }) => {
  if (!target.matches('.remove-excluded-date')) return;
  target.closest('.excluded-date-row').remove();
  validateForm();
});

function clearCaseRow(row) {
  row.querySelectorAll('.case-cell input').forEach(input => {
    if (input.type === 'checkbox') input.checked = true;
    else input.value = '';
    input.setCustomValidity('');
  });
  const planned = row.querySelector('[data-planned-date]');
  planned.calculationRequest = null;
  planned.textContent = plannedDateHint;
}

function addCaseRow(panel) {
  const rows = panel.querySelector('tbody');
  const row = rows.insertRow();
  rows.rows[0].querySelectorAll('.case-cell').forEach(cell => row.append(cell.cloneNode(true)));
  clearCaseRow(row);
  rows.querySelector('.shared').rowSpan = rows.rows.length;
  return row;
}

panels.forEach(panel => {
  const completionDates = panel.querySelector('.completion-dates');
  if (panel.dataset.report === 'technical') completionDates.hidden = false;
  else completionDates.remove();

  const rows = panel.querySelector('tbody');

  panel.querySelector('.add-case').addEventListener('click', () => {
    const row = addCaseRow(panel);
    updateCaseNumbers(panel);
    updatePanelPlannedDates(panel);
    validateForm();
    row.querySelector('[data-date]').focus();
  });

  rows.addEventListener('click', ({ target }) => {
    if (!target.matches('.remove-case')) return;
    const row = target.closest('tr');
    row.querySelector('[data-planned-date]').calculationRequest = null;
    if (rows.rows.length === 1) clearCaseRow(row);
    else {
      const shared = row.querySelector('.shared');
      if (shared) rows.rows[1].append(shared);
      row.remove();
    }
    rows.querySelector('.shared').rowSpan = rows.rows.length;
    updateCaseNumbers(panel);
    updatePanelPlannedDates(panel);
    validateForm();
  });
});

reportForm.addEventListener('reset', event => {
  event.preventDefault();
  reportForm.querySelectorAll('[data-planned-date]').forEach(cell => cell.calculationRequest = null);
  panels.forEach(panel => {
    const rows = panel.querySelector('tbody');
    [...rows.rows].slice(1).forEach(row => row.remove());
    clearCaseRow(rows.rows[0]);
    rows.querySelector('.shared').rowSpan = 1;
    updateCaseNumbers(panel);
  });
  reportForm.querySelectorAll('input').forEach(input => {
    if (input.type !== 'checkbox' && !input.readOnly) input.value = '';
    input.setCustomValidity('');
  });
  calculationDate.value = today();
  excludedDateRows.replaceChildren();
  result.textContent = '';
  calculationResult.replaceChildren();
  calculationResult.hidden = true;
  tabs[0].click();
  updateOverdueWarnings();
});

function validateForm() {
  contractNumber.setCustomValidity(/^(0[1-9]|[1-7][0-9]|80)$/.test(contractNumber.value) ? '' : 'Введите номер от 01 до 80.');
  stageNumber.setCustomValidity(/^\d+$/.test(stageNumber.value) && Number(stageNumber.value) >= 1 && Number(stageNumber.value) <= 2147483647 ? '' : 'Укажите номер этапа целым числом от 1.');
  stageAmount.setCustomValidity(/^\d+([.,]\d{1,2})?$/.test(stageAmount.value.replace(/\s/g, '')) ? '' : 'Укажите сумму с точностью до двух знаков после запятой.');
  const asOf = parseDate(calculationDate.value);
  reportForm.querySelectorAll('[data-date]').forEach(input => {
    let error = dateError(input.value);
    if (!error && input.value && input !== calculationDate && asOf && parseDate(input.value) > asOf) error = 'Дата не может быть позже даты расчёта.';
    input.setCustomValidity(error);
  });
  reportForm.querySelectorAll('[data-field="remarksCorrected"]').forEach(input => input.setCustomValidity(''));
  const submitted = submissionDates.querySelector('[name="reportActualSubmissionDate"]');
  for (const panel of panels) {
    let previous = submitted;
    for (const row of panel.querySelector('tbody').rows) {
      const returned = row.querySelector('[data-field="returnedForRevisionDate"]');
      const actual = row.querySelector('[data-field="actualSubmissionDate"]');
      validateDateOrder(returned, previous, 'Возврат на доработку не может предшествовать предыдущей подаче отчёта.');
      validateDateOrder(actual, returned, 'Подача доработанного отчёта не может предшествовать возврату.');
      previous = actual;
    }
    const approval = panel.querySelector('[name="reportApprovalDate"]');
    validateDateOrder(approval, previous, 'Согласование не может предшествовать последней подаче отчёта.');
    const corrected = panel.querySelector('tbody').lastElementChild.querySelector('[data-field="remarksCorrected"]');
    corrected.setCustomValidity(corrected.checked ? '' : 'Исправьте замечания последнего кейса или добавьте повторную доработку.');
  }
  updateOverdueWarnings();
}

function validateDateOrder(input, previous, message) {
  if (input.validity.valid && previous.validity.valid && parseDate(input.value) < parseDate(previous.value)) {
    input.setCustomValidity(message);
  }
}

function updateOverdueWarnings() {
  const calendarDay = date => Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
  const daysToExcludeManually = new Set([...excludedDateRows.querySelectorAll('input')]
    .map(input => parseDate(input.value)).filter(Boolean).map(calendarDay));
  reportForm.querySelectorAll('[data-field="actualSubmissionDate"]').forEach(input => {
    const planned = parseDate(input.closest('tr').querySelector('[data-planned-date]').textContent);
    const actual = parseDate(input.value);
    let days = 0;
    if (planned && actual && input.validity.valid) {
      const start = calendarDay(planned);
      const end = calendarDay(actual);
      days = (end - start) / 86400000;
      days -= [...daysToExcludeManually].filter(day => day > start && day <= end).length;
    }
    const cell = input.closest('td');
    const note = cell.querySelector('.overdue-note');
    const message = days > 30 ? `Просрочка: ${days} дн. (более 30).` : '';
    cell.classList.toggle('overdue-warning', days > 30);
    note.textContent = message;
    note.hidden = !message;
    if (message) input.setAttribute('aria-description', message);
    else input.removeAttribute('aria-description');
  });
}

function updateCaseNumbers(panel) {
  let number = 1;
  for (const row of panel.querySelector('tbody').rows) {
    row.querySelector('[data-field="number"]').value = number;
    if (row.querySelector('[data-field="remarksCorrected"]').checked) number++;
  }
}

function updatePanelPlannedDates(panel) {
  panel.querySelectorAll('[data-field="returnedForRevisionDate"]').forEach(updatePlannedDate);
}

async function updatePlannedDate(input) {
  const row = input.closest('tr');
  const cell = row.querySelector('[data-planned-date]');
  const request = Symbol();
  cell.calculationRequest = request;
  const caseNumber = Number(row.querySelector('[data-field="number"]').value);
  const firstRow = [...row.parentElement.rows].slice(0, row.sectionRowIndex + 1)
    .find(candidate => Number(candidate.querySelector('[data-field="number"]').value) === caseNumber);
  const value = firstRow?.querySelector('[data-field="returnedForRevisionDate"]').value;
  cell.textContent = plannedDateHint;
  updateOverdueWarnings();
  if (!Number.isInteger(caseNumber) || caseNumber < 1 || !value || dateError(value)) return;
  cell.textContent = '…';
  result.textContent = '';
  try {
    const params = new URLSearchParams({ caseNumber, returnedForRevisionDate: value });
    const response = await fetch(`/api/reports/revision-planned-date?${params}`);
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || 'Не удалось рассчитать дату. Проверьте наличие календаря для выбранного года.');
    if (cell.calculationRequest === request) {
      cell.textContent = data.plannedSubmissionDate;
      updateOverdueWarnings();
    }
  } catch (error) {
    if (cell.calculationRequest === request) {
      cell.textContent = 'Ошибка';
      result.textContent = error.message;
    }
  }
}

function appendRow(parent, values, heading = false) {
  const row = document.createElement('tr');
  values.forEach(value => {
    const cell = document.createElement(heading ? 'th' : 'td');
    if (heading) cell.scope = 'col';
    cell.textContent = value;
    row.append(cell);
  });
  parent.append(row);
}

function textElement(tag, text, className = '') {
  const element = document.createElement(tag);
  element.textContent = text;
  element.className = className;
  return element;
}

function appendTable(parent, label, headings, rows) {
  const scroll = document.createElement('div');
  scroll.className = 'table-scroll';
  const table = document.createElement('table');
  table.setAttribute('aria-label', label);
  const head = document.createElement('thead');
  const body = document.createElement('tbody');
  appendRow(head, headings, true);
  rows.forEach(values => appendRow(body, values));
  table.append(head, body);
  scroll.append(table);
  parent.append(scroll);
}

function overdueDaySteps(item) {
  if (!item.daysToExcludeManually.length) return [`Дни просрочки (${item.overdueDays.length}): ${item.daysForCalculation}.`];
  return [
    `Исходные дни (${item.overdueDays.length}): ${item.overdueDays.join(', ') || 'нет'}.`,
    `Исключены вручную (${item.daysToExcludeManually.length}): ${item.daysToExcludeManually.join(', ') || 'нет'}.`,
    `Остались для расчёта: ${item.daysForCalculation}.`,
  ];
}

function renderCalculation(data) {
  calculationResult.replaceChildren();
  calculationResult.append(
    textElement('h2', `Расчёт по договору ${data.contractNumber}, этап ${data.stageNumber}`),
    textElement('p', `Дата расчёта: ${data.calculationDate}. Сумма этапа: ${data.stageAmount}. Ключевая ставка ЦБ: ${data.keyRatePercent} на дату расчёта.`),
    textElement('p', data.formula),
    textElement('p', data.overdueDaysExplanation),
    textElement('p', `Итоговая неустойка: ${data.totalPenalty}`, 'penalty-total'),
  );
  [data.initialSubmissionPenalty, data.revisionsPenalty, data.stageCompletionPenalty].forEach(stage => {
    const section = document.createElement('section');
    section.className = 'penalty-stage';
    section.append(textElement('h3', stage.title));
    if (stage === data.revisionsPenalty) {
      const reports = [stage.grantReport, stage.extraBudgetReport, stage.technicalReport];
      appendTable(section, 'Доработка каждого отчёта', ['Отчёт', 'Расчёт дней просрочки', 'Дни'],
        reports.map(report => [report.name, overdueDaySteps(report).join('\n'), report.overdueDaysCount]));
    }
    if (stage !== data.stageCompletionPenalty && stage.daysToExcludeManually.length) {
      const steps = document.createElement('ol');
      steps.className = 'exclusion-steps';
      overdueDaySteps(stage).forEach(step => steps.append(textElement('li', step)));
      section.append(steps);
    } else if (stage !== data.stageCompletionPenalty) {
      section.append(textElement('p', overdueDaySteps(stage)[0], 'overdue-days'));
    } else {
      section.append(textElement('p', `Конкретные дни: ${stage.overdueDays}`, 'overdue-days'));
    }
    section.append(textElement('p', stage.summary, 'penalty-subtotal'));
    if (stage.formula) section.append(textElement('p', stage.formula, 'penalty-formula'));
    if (stage === data.revisionsPenalty) {
      const reports = [stage.grantReport, stage.extraBudgetReport, stage.technicalReport];
      section.append(textElement('p', stage.explanation));
      const details = document.createElement('details');
      details.append(
        textElement('summary', 'Проверить сроки и исправление замечаний по кейсам'),
        textElement('p', stage.caseExplanation),
      );
      appendTable(details, 'Сроки доработки по кейсам', ['Отчёт', 'Кейс', 'Возврат', 'Плановая подача', 'Фактическая подача', 'Замечания', 'Расчёт просрочки'],
        reports.flatMap(report => report.cases.map(item => [
          report.name, item.caseNumber, item.returnedForRevisionDate,
          item.plannedSubmissionDate, item.actualSubmissionDate,
          item.remarks, overdueDaySteps(item).join('\n'),
        ])));
      section.append(details);
    }
    calculationResult.append(section);
  });
  calculationResult.append(textElement('p', `Итого: ${data.totalFormula}`, 'penalty-total'));
  calculationResult.hidden = false;
}

async function postRequest(path, data) {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    const error = await response.json();
    throw Error(error.detail || 'Не удалось выполнить запрос. Проверьте данные и повторите попытку.');
  }
  return response;
}

reportForm.addEventListener('submit', async event => {
  event.preventDefault();
  const data = validatedRequest();
  if (!data) return;
  submit.disabled = true;
  result.textContent = '';
  calculationResult.hidden = true;
  const loadingTime = showCalculationCat();
  try {
    const response = await postRequest('/api/reports/penalty-calculation', data);
    const calculation = await response.json();
    renderCalculation(calculation);
    result.textContent = `Неустойка: ${calculation.totalPenalty}.`;
  } catch (error) {
    result.textContent = error.message;
  } finally {
    await loadingTime;
    hideCalculationCat();
    submit.disabled = false;
  }
});

document.querySelector('#export-report').addEventListener('click', async event => {
  const data = validatedRequest();
  if (!data) return;
  const button = event.currentTarget;
  button.disabled = true;
  result.textContent = 'Готовим Excel…';
  try {
    const response = await postRequest('/api/reports/penalty-calculation/export', data);
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    const disposition = response.headers.get('Content-Disposition') || '';
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    link.download = encodedName ? decodeURIComponent(encodedName) : disposition.match(/filename="?([^";]+)"?/i)?.[1] || 'penalty_calculation.xlsx';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
    result.textContent = 'Файл XLSX подготовлен.';
  } catch (error) {
    result.textContent = error.message;
  } finally {
    button.disabled = false;
  }
});
