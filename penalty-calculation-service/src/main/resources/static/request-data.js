// Преобразование полей формы в объект запроса и обратно.
function reportData(panel) {
  const data = { reportApprovalDate: panel.querySelector('[name="reportApprovalDate"]').value };
  if (panel.dataset.report === 'technical') {
    data.plannedCompletionDate = panel.querySelector('[name="plannedCompletionDate"]').value;
    data.actualCompletionDate = panel.querySelector('[name="actualCompletionDate"]').value;
  }
  data.cases = [...panel.querySelector('tbody').rows].map(row => ({
    returnedForRevisionDate: row.querySelector('[data-field="returnedForRevisionDate"]').value,
    actualSubmissionDate: row.querySelector('[data-field="actualSubmissionDate"]').value,
    remarksCorrected: row.querySelector('[data-field="remarksCorrected"]').checked,
  }));
  return data;
}

function getRequestData() {
  return {
    calculationDate: calculationDate.value,
    contractNumber: `Г-00${contractNumber.value}`,
    stageNumber: stageNumber.value ? Number(stageNumber.value) : null,
    stageAmount: stageAmount.value.replace(/\s/g, '').replace(',', '.'),
    reportPlannedSubmissionDate: submissionDates.querySelector('[name="reportPlannedSubmissionDate"]').value,
    reportActualSubmissionDate: submissionDates.querySelector('[name="reportActualSubmissionDate"]').value,
    grantReport: reportData(panels[0]),
    extraBudgetReport: reportData(panels[1]),
    technicalReport: reportData(panels[2]),
    daysToExcludeManually: [...excludedDateRows.querySelectorAll('input')].map(input => input.value),
  };
}

function checkRequestData(data) {
  const reports = ['grantReport', 'extraBudgetReport', 'technicalReport'];
  checkObjectFields(data, 'Запрос', ['calculationDate', 'contractNumber', 'stageNumber', 'stageAmount', 'reportPlannedSubmissionDate', 'reportActualSubmissionDate', 'daysToExcludeManually', ...reports]);
  checkDateFields(data, 'Запрос', ['calculationDate', 'reportPlannedSubmissionDate', 'reportActualSubmissionDate']);
  if ('contractNumber' in data && (typeof data.contractNumber !== 'string' || !/^Г-00\d{0,2}$/.test(data.contractNumber))) throw Error('contractNumber: ожидается номер с префиксом Г-00 и двумя цифрами.');
  if ('stageNumber' in data && data.stageNumber !== null && (!Number.isInteger(data.stageNumber) || data.stageNumber < 1 || data.stageNumber > 2147483647)) throw Error('stageNumber: укажите номер этапа целым числом от 1.');
  if ('stageAmount' in data && !['string', 'number'].includes(typeof data.stageAmount)) throw Error('stageAmount: ожидается сумма строкой или числом.');
  if (typeof data.stageAmount === 'number' && Math.abs(data.stageAmount) > Number.MAX_SAFE_INTEGER) throw Error('stageAmount: передайте большую сумму строкой для сохранения точности.');
  if ('stageAmount' in data && !/^\d*([.,]\d{0,2})?$/.test(String(data.stageAmount))) throw Error('stageAmount: укажите только цифры и до двух знаков после запятой.');
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
  calculationDate.value = data.calculationDate ?? '';
  contractNumber.value = data.contractNumber?.slice(4) ?? '';
  stageNumber.value = data.stageNumber ?? '';
  stageAmount.value = data.stageAmount ?? '';
  submissionDates.querySelectorAll('[name]').forEach(input => input.value = data[input.name] ?? '');
  for (const panel of panels) {
    const report = data[`${panel.dataset.report}Report`] ?? {};
    const rows = panel.querySelector('tbody');
    const cases = report.cases ?? [];
    while (rows.rows.length < cases.length) addCaseRow(panel);
    cases.forEach((item, index) => {
      const row = rows.rows[index];
      row.querySelectorAll('[data-field][data-date]').forEach(input => input.value = item[input.dataset.field] ?? '');
      row.querySelector('[data-field="remarksCorrected"]').checked = item.remarksCorrected ?? true;
    });
    panel.querySelectorAll('[name][data-date]').forEach(input => input.value = report[input.name] ?? '');
    updateCaseNumbers(panel);
    updatePanelPlannedDates(panel);
  }
  for (const value of data.daysToExcludeManually ?? []) addExcludedDate(value);
  formatAmount(stageAmount);
  validateForm();
  updateJsonFromForm();
}

