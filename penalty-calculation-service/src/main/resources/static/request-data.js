// Преобразование полей формы в объект запроса и обратно.
function reportData(panel) {
  const data = Object.fromEntries([...panel.querySelectorAll('[name][data-date]')].filter(input => input.value).map(input => [input.name, input.value]));
  data.cases = [...panel.querySelector('tbody').rows].filter(row => row.dataset.requestCase ||
    [...row.querySelectorAll('[data-date]')].some(input => input.value) || !row.querySelector('[data-field="remarksCorrected"]').checked)
    .map(row => ({
      ...Object.fromEntries([...row.querySelectorAll('[data-field][data-date]')].filter(input => input.value).map(input => [input.dataset.field, input.value])),
      remarksCorrected: row.querySelector('[data-field="remarksCorrected"]').checked,
    }));
  return data;
}

function getRequestData() {
  const data = {
    calculationDate: calculationDate.value,
    ...(contractNumber.value ? { contractNumber: `Г-00${contractNumber.value}` } : {}),
    ...(stageAmount.value ? { stageAmount: stageAmount.value.replace(/\s/g, '').replace(',', '.') } : {}),
    ...Object.fromEntries([...submissionDates.querySelectorAll('[name]')].filter(input => input.value).map(input => [input.name, input.value])),
  };
  panels.forEach(panel => {
    const report = reportData(panel);
    if (panel.dataset.requestReport || report.cases.length || Object.keys(report).length > 1) data[`${panel.dataset.report}Report`] = report;
  });
  data.daysToExcludeManually = [...excludedDateRows.querySelectorAll('input')].map(input => input.value).filter(Boolean);
  return data;
}

function checkRequestData(data) {
  const reports = ['grantReport', 'extraBudgetReport', 'technicalReport'];
  checkObjectFields(data, 'Запрос', ['calculationDate', 'contractNumber', 'stageAmount', 'reportPlannedSubmissionDate', 'reportActualSubmissionDate', 'daysToExcludeManually', ...reports]);
  checkDateFields(data, 'Запрос', ['calculationDate', 'reportPlannedSubmissionDate', 'reportActualSubmissionDate']);
  if ('contractNumber' in data && (typeof data.contractNumber !== 'string' || !/^Г-00\d{0,2}$/.test(data.contractNumber))) throw Error('contractNumber: ожидается номер с префиксом Г-00 и двумя цифрами.');
  if ('stageAmount' in data && !['string', 'number'].includes(typeof data.stageAmount)) throw Error('stageAmount: ожидается сумма строкой или числом.');
  if (typeof data.stageAmount === 'number' && Math.abs(data.stageAmount) > Number.MAX_SAFE_INTEGER) throw Error('stageAmount: передайте большую сумму строкой для сохранения точности.');
  if ('stageAmount' in data && !/^\d+([.,]\d{1,2})?$/.test(String(data.stageAmount))) throw Error('stageAmount: укажите только цифры и до двух знаков после запятой.');
  for (const name of reports) {
    if (!(name in data)) continue;
    const report = data[name];
    const dateFields = ['reportApprovalDate', ...(name === 'technicalReport' ? ['plannedCompletionDate', 'actualCompletionDate'] : [])];
    checkObjectFields(report, name, ['cases', ...dateFields]);
    checkDateFields(report, name, dateFields);
    if ('cases' in report) {
      if (!Array.isArray(report.cases)) throw Error(`${name}.cases: ожидается массив кейсов.`);
      report.cases.forEach((item, index) => {
        const path = `${name}.cases[${index}]`;
        checkObjectFields(item, path, ['returnedForRevisionDate', 'actualSubmissionDate', 'remarksCorrected']);
        checkDateFields(item, path, ['returnedForRevisionDate', 'actualSubmissionDate']);
        if ('remarksCorrected' in item && typeof item.remarksCorrected !== 'boolean') throw Error(`${path}.remarksCorrected: ожидается true или false.`);
      });
    }
  }
  if ('daysToExcludeManually' in data && (!Array.isArray(data.daysToExcludeManually) || data.daysToExcludeManually.some(value => typeof value !== 'string'))) throw Error('daysToExcludeManually: ожидается массив строк с датами.');
}

function checkObjectFields(value, path, allowedFields) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error(`${path}: ожидается объект.`);
  const extra = Object.keys(value).filter(key => !allowedFields.includes(key));
  if (extra.length) throw Error(`${path}: неизвестные поля ${extra.join(', ')}. Они не могут быть перенесены в форму.`);
}

function checkDateFields(value, path, fields) {
  for (const field of fields) {
    if (field in value && value[field] !== null && typeof value[field] !== 'string') {
      throw Error(`${path}.${field}: укажите дату строкой дд.мм.гггг.`);
    }
  }
}

function fillRequestData(data) {
  checkRequestData(data);
  reportForm.reset();
  calculationDate.value = 'calculationDate' in data ? data.calculationDate ?? '' : today();
  contractNumber.value = data.contractNumber?.slice(4) ?? '';
  stageAmount.value = data.stageAmount ?? '';
  submissionDates.querySelectorAll('[name]').forEach(input => input.value = data[input.name] ?? '');
  for (const panel of panels) {
    const report = data[`${panel.dataset.report}Report`];
    if (!report) continue;
    panel.dataset.requestReport = 'true';
    const rows = panel.querySelector('tbody');
    const cases = report.cases ?? [];
    while (rows.rows.length < cases.length) panel.querySelector('.add-case').click();
    cases.forEach((item, index) => {
      const row = rows.rows[index];
      row.dataset.requestCase = 'true';
      row.querySelectorAll('[data-field][data-date]').forEach(input => input.value = item[input.dataset.field] ?? '');
      row.querySelector('[data-field="remarksCorrected"]').checked = item.remarksCorrected ?? true;
    });
    panel.querySelectorAll('[name][data-date]').forEach(input => input.value = report[input.name] ?? '');
    updateCaseNumbers(panel);
    updatePanelPlannedDates(panel);
  }
  for (const value of data.daysToExcludeManually ?? []) {
    document.querySelector('#add-excluded-date').click();
    excludedDateRows.lastElementChild.querySelector('input').value = value;
  }
  stageAmount.dispatchEvent(new Event('input', { bubbles: true }));
  validateForm();
  updateJsonFromForm();
}

