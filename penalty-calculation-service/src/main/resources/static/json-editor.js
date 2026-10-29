// Переключение формы и JSON, разбор текста и синхронизация даты расчёта.
const formEditor = document.querySelector('#form-editor');
const jsonPanel = document.querySelector('#json-panel');
const jsonEditor = document.querySelector('#json-editor');

function showEditor(mode) {
  formEditor.hidden = mode === 'json';
  jsonPanel.hidden = mode !== 'json';
  document.querySelector('#form-mode').setAttribute('aria-pressed', String(mode === 'form'));
  document.querySelector('#json-mode').setAttribute('aria-pressed', String(mode === 'json'));
}
document.querySelector('#json-mode').addEventListener('click', () => {
  if (!jsonPanel.hidden) return;
  updateJsonFromForm();
  showEditor('json');
  result.textContent = '';
});
document.querySelector('#form-mode').addEventListener('click', () => {
  if (jsonPanel.hidden) return;
  try {
    fillRequestData(readJsonRequest());
    showEditor('form');
  } catch (error) {
    result.textContent = error.message;
    jsonEditor.focus();
  }
});
jsonEditor.addEventListener('input', () => {
  result.textContent = '';
  try {
    const data = JSON.parse(jsonEditor.value);
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      calculationDate.value = typeof data.calculationDate === 'string' ? data.calculationDate : '';
      stageNumber.value = Number.isInteger(data.stageNumber) ? data.stageNumber : '';
    }
  } catch { /* Незавершённый JSON сохраняется до исправления. */ }
});
reportForm.addEventListener('input', ({ target }) => {
  if ((target === calculationDate || target === stageNumber) && !jsonPanel.hidden) {
    try {
      const data = readJsonRequest();
      if (target === calculationDate) data.calculationDate = calculationDate.value;
      else data.stageNumber = stageNumber.value ? Number(stageNumber.value) : null;
      jsonEditor.value = JSON.stringify(data, null, 2);
    } catch (error) {
      result.textContent = `Поле не перенесено в запрос: ${error.message}`;
    }
  }
});
reportForm.addEventListener('reset', updateJsonFromForm);

function updateJsonFromForm() {
  jsonEditor.value = JSON.stringify(getRequestData(), null, 2);
}

function readJsonRequest() {
  try {
    const data = JSON.parse(jsonEditor.value);
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw Error('Ожидается объект запроса.');
    return data;
  } catch (error) { throw Error(`Ошибка JSON: ${error.message}`); }
}

function validatedRequest() {
  if (!jsonPanel.hidden) {
    try {
      fillRequestData(readJsonRequest());
    } catch (error) {
      result.textContent = error.message;
      jsonEditor.focus();
      return null;
    }
  }
  validateForm();
  const invalid = reportForm.querySelector('input:invalid');
  if (invalid) {
    if (!jsonPanel.hidden && formEditor.contains(invalid)) {
      result.textContent = `Проверьте JSON: ${invalid.validationMessage}`;
      jsonEditor.focus();
      return null;
    }
    const panel = invalid.closest('[role="tabpanel"]');
    if (panel) tabs[panels.indexOf(panel)].click();
    invalid.reportValidity();
    return null;
  }
  return getRequestData();
}

