// Временное заполнение данными из PenaltyCalculationServiceTest: кнопка 🐞 или fillTestData() в консоли.
// Для удаления кнопки и заполнения достаточно убрать подключение test-data.js из index.html.
(() => {
  const button = document.createElement('button');
  button.type = 'button';
  button.title = 'Заполнить тестовые данные';
  button.setAttribute('aria-label', button.title);
  button.textContent = '🐞';
  button.style.cssText = `
    position: fixed; right: 16px; bottom: 16px; z-index: 10;
    width: 40px; height: 40px; padding: 0; border-radius: 50%;
    background: white; font-size: 22px; line-height: 1;
    box-shadow: 0 2px 8px #0002;
  `;
  button.addEventListener('click', () => fillTestData());
  document.body.append(button);
})();

function fillTestData() {
  const form = document.querySelector('#reports-form');
  form.reset();
  const grant = document.querySelector('[role="tabpanel"][data-report="grant"]');
  const extraBudget = document.querySelector('[role="tabpanel"][data-report="extraBudget"]');
  const technical = document.querySelector('[role="tabpanel"][data-report="technical"]');
  const fill = (input, value) => {
    input.focus();
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  };

  const fillCase = (panel, index, returned, submitted, corrected = true) => {
    const rows = panel.querySelector('tbody');
    while (rows.rows.length <= index) panel.querySelector('.add-case').click();
    const row = rows.rows[index];
    fill(row.querySelector('[data-field="returnedForRevisionDate"]'), returned);
    fill(row.querySelector('[data-field="actualSubmissionDate"]'), submitted);
    const checkbox = row.querySelector('[data-field="remarksCorrected"]');
    if (checkbox.checked !== corrected) checkbox.click();
  };

  fill(form.querySelector('[name="contractNumber"]'), '29');
  fill(form.querySelector('[name="stageAmount"]'), '50000000');
  fill(form.querySelector('[name="reportPlannedSubmissionDate"]'), '23.12.2025');
  fill(form.querySelector('[name="reportActualSubmissionDate"]'), '25.12.2025');

  document.querySelector('#tab-grant').click();
  fillCase(grant, 0, '20.01.2026', '23.01.2026');
  fillCase(grant, 1, '26.01.2026', '28.01.2026');
  fillCase(grant, 2, '06.03.2026', '12.03.2026');
  fillCase(grant, 3, '13.03.2026', '18.03.2026');
  fill(grant.querySelector('[name="reportApprovalDate"]'), '19.03.2026');

  document.querySelector('#tab-extra-budget').click();
  fillCase(extraBudget, 0, '20.01.2026', '23.01.2026');
  fillCase(extraBudget, 1, '26.01.2026', '29.01.2026');
  fillCase(extraBudget, 2, '06.03.2026', '12.03.2026');
  fillCase(extraBudget, 3, '13.03.2026', '19.03.2026');
  fill(extraBudget.querySelector('[name="reportApprovalDate"]'), '19.03.2026');

  document.querySelector('#tab-technical').click();
  fillCase(technical, 0, '20.01.2026', '23.01.2026');
  fillCase(technical, 1, '26.01.2026', '30.01.2026', false);
  fillCase(technical, 2, '13.02.2026', '16.02.2026');
  fillCase(technical, 3, '06.03.2026', '12.03.2026');
  fillCase(technical, 4, '13.03.2026', '16.03.2026');
  fillCase(technical, 5, '16.03.2026', '17.03.2026');
  fillCase(technical, 6, '18.03.2026', '18.03.2026');
  fill(technical.querySelector('[name="reportApprovalDate"]'), '19.03.2026');
  fill(technical.querySelector('[name="plannedCompletionDate"]'), '22.12.2025');
  fill(technical.querySelector('[name="actualCompletionDate"]'), '10.01.2026');
}
