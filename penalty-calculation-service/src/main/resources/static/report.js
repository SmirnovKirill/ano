const tabs = [...document.querySelectorAll('[role="tab"]')];
const template = document.querySelector('#report-form');
const reportForm = document.querySelector('#reports-form');
const heading = document.querySelector('h1');
const expensesTitle = heading.textContent;
const contractNumber = document.querySelector('#contract-number');
const submissionDates = document.querySelector('.submission-dates');
const calculationInputs = document.querySelector('.calculation-inputs');
const result = document.querySelector('#result');
const calculationResult = document.querySelector('#calculation-result');
const submit = document.querySelector('#submit-report');
const loader = document.querySelector('#calculation-loader');
const plannedDateHint = template.querySelector('[data-planned-date]').textContent;
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
loader.addEventListener('cancel', event => event.preventDefault());
loader.addEventListener('keydown', event => {
  if (event.key === 'Escape') event.preventDefault();
});

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
  heading.textContent = tab.dataset.report === 'technical' ? 'Расчет неустойки по этапу' : expensesTitle;
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

reportForm.addEventListener('change', ({ target }) => {
  if (!target.matches('[data-field="remarksCorrected"]')) return;
  const panel = target.closest('[role="tabpanel"]');
  updateCaseNumbers(panel);
  updatePanelPlannedDates(panel);
  validateForm();
});

panels.forEach(panel => {
  const completionDates = panel.querySelector('.completion-dates');
  if (panel.dataset.report === 'technical') completionDates.hidden = false;
  else completionDates.remove();

  const rows = panel.querySelector('tbody');

  panel.querySelector('.add-case').addEventListener('click', () => {
    const row = rows.insertRow();
    rows.rows[0].querySelectorAll('.case-cell').forEach(cell => row.append(cell.cloneNode(true)));
    row.querySelector('[data-planned-date]').textContent = plannedDateHint;
    row.querySelectorAll('input').forEach(input => {
      if (input.type === 'checkbox') input.checked = true;
      else input.value = '';
    });
    updateCaseNumbers(panel);
    updatePanelPlannedDates(panel);
    rows.querySelectorAll('.shared').forEach(cell => cell.rowSpan = rows.rows.length);
    validateForm();
    row.querySelector('[data-date]').focus();
  });

});

function validateForm() {
  contractNumber.setCustomValidity(contractNumber.value.trim() ? '' : 'Укажите номер договора.');
  reportForm.querySelectorAll('[data-date]').forEach(input => input.setCustomValidity(dateError(input.value)));
  reportForm.querySelectorAll('[data-field="remarksCorrected"]').forEach(input => input.setCustomValidity(''));
  const submitted = submissionDates.querySelector('[name="reportActualSubmissionDate"]');
  const notBefore = (input, previous, message) => {
    if (input.validity.valid && previous.validity.valid && parseDate(input.value) < parseDate(previous.value)) {
      input.setCustomValidity(message);
    }
  };
  for (const panel of panels) {
    let previous = submitted;
    for (const row of panel.querySelector('tbody').rows) {
      const returned = row.querySelector('[data-field="returnedForRevisionDate"]');
      const actual = row.querySelector('[data-field="actualSubmissionDate"]');
      notBefore(returned, previous, 'Возврат на доработку не может предшествовать предыдущей подаче отчёта.');
      notBefore(actual, returned, 'Подача доработанного отчёта не может предшествовать возврату.');
      previous = actual;
    }
    const approval = panel.querySelector('[name="reportApprovalDate"]');
    notBefore(approval, previous, 'Согласование не может предшествовать последней подаче отчёта.');
    const corrected = panel.querySelector('tbody').lastElementChild.querySelector('[data-field="remarksCorrected"]');
    corrected.setCustomValidity(corrected.checked ? '' : 'Исправьте замечания последнего кейса или добавьте повторную доработку.');
  }
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
  if (!Number.isInteger(caseNumber) || caseNumber < 1 || !value || dateError(value)) return;
  cell.textContent = '…';
  result.textContent = '';
  try {
    const params = new URLSearchParams({ caseNumber, returnedForRevisionDate: value });
    const response = await fetch(`/api/reports/revision-planned-date?${params}`);
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || 'Не удалось рассчитать дату. Проверьте наличие календаря для выбранного года.');
    if (cell.calculationRequest === request) cell.textContent = data.plannedSubmissionDate;
  } catch (error) {
    if (cell.calculationRequest === request) {
      cell.textContent = 'Ошибка';
      result.textContent = error.message;
    }
  }
}

function reportData(panel) {
  const data = Object.fromEntries([...panel.querySelectorAll('[name][data-date]')].map(input => [input.name, input.value]));
  data.cases = [...panel.querySelector('tbody').rows].map(row => ({
    returnedForRevisionDate: row.querySelector('[data-field="returnedForRevisionDate"]').value,
    actualSubmissionDate: row.querySelector('[data-field="actualSubmissionDate"]').value,
    remarksCorrected: row.querySelector('[data-field="remarksCorrected"]').checked,
  }));
  return data;
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

function renderCalculation(data) {
  calculationResult.replaceChildren();
  calculationResult.append(
    textElement('h2', `Расчёт по договору ${data.contractNumber}`),
    textElement('p', `Дата расчёта: ${data.calculationDate}. Сумма этапа: ${data.stageAmount}. Ключевая ставка ЦБ: ${data.keyRatePercent} на дату расчёта.`),
    textElement('p', data.formula),
    textElement('p', data.overdueDaysExplanation),
    textElement('p', `Итоговая неустойка: ${data.totalPenalty}`, 'penalty-total'),
  );
  [data.initialSubmissionPenalty, data.revisionsPenalty, data.stageCompletionPenalty].forEach(stage => {
    const section = document.createElement('section');
    section.className = 'penalty-stage';
    section.append(
      textElement('h3', stage.title),
      textElement('p', stage.summary, 'penalty-subtotal'),
      textElement('p', `Конкретные дни: ${stage.overdueDays}`, 'overdue-days'),
      textElement('p', stage.formula, 'penalty-formula'),
    );
    if (stage === data.revisionsPenalty) {
      const reports = [stage.grantReport, stage.extraBudgetReport, stage.technicalReport];
      appendTable(section, 'Доработка каждого отчёта', ['Отчёт', 'Конкретные дни просрочки', 'Дни'],
        reports.map(report => [report.name, report.overdueDays, report.overdueDaysCount]));
      section.append(textElement('p', stage.explanation));
      const details = document.createElement('details');
      details.append(
        textElement('summary', 'Проверить сроки и исправление замечаний по кейсам'),
        textElement('p', stage.caseExplanation),
      );
      appendTable(details, 'Сроки доработки по кейсам', ['Отчёт', 'Кейс', 'Возврат', 'Плановая подача', 'Фактическая подача', 'Замечания', 'Просрочка'],
        reports.flatMap(report => report.cases.map(item => [
          report.name, item.caseNumber, item.returnedForRevisionDate,
          item.plannedSubmissionDate, item.actualSubmissionDate,
          item.remarks, item.overdueDays,
        ])));
      section.append(details);
    }
    calculationResult.append(section);
  });
  calculationResult.append(textElement('p', `Итого: ${data.totalFormula}`, 'penalty-total'));
  calculationResult.hidden = false;
}

reportForm.addEventListener('submit', async event => {
  event.preventDefault();
  contractNumber.value = contractNumber.value.trim();
  validateForm();
  const invalid = reportForm.querySelector('input:invalid');
  if (invalid) {
    const panel = invalid.closest('[role="tabpanel"]');
    if (panel) tabs[panels.indexOf(panel)].click();
    invalid.reportValidity();
    return;
  }
  const data = {
    contractNumber: contractNumber.value,
    ...Object.fromEntries([...calculationInputs.querySelectorAll('[name]')].map(input => [input.name, input.value])),
    ...Object.fromEntries([...submissionDates.querySelectorAll('[name]')].map(input => [input.name, input.value])),
  };
  panels.forEach(panel => data[`${panel.dataset.report}Report`] = reportData(panel));
  submit.disabled = true;
  result.textContent = '';
  calculationResult.hidden = true;
  loader.showModal();
  const loadingTime = new Promise(resolve => setTimeout(resolve, 1000));
  try {
    const response = await fetch('/api/reports/penalty-calculation', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    const calculation = await response.json();
    if (!response.ok) throw new Error(calculation.detail || 'Не удалось рассчитать неустойку. Проверьте данные и повторите попытку.');
    await loadingTime;
    renderCalculation(calculation);
    result.textContent = `Неустойка: ${calculation.totalPenalty}.`;
  } catch (error) {
    await loadingTime;
    result.textContent = error.message;
  } finally {
    loader.close();
    submit.disabled = false;
  }
});
