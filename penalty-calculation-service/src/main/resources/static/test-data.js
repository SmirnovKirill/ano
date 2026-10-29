// Временные примеры: каждый файл test-case-N.json содержит только исходный запрос.
// Чтобы убрать кнопки, удалите подключение test-data.js из index.html.
for (const [number, title] of [[1, 'РОГА И КОРЫТА'], [2, 'ИНТРА — этап 3'], [3, 'РУМО — этап 1']]) {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = '🐞';
  button.title = `Пример ${number}: ${title}`;
  button.setAttribute('aria-label', button.title);
  button.dataset.testCase = number;
  button.style.cssText = 'width:40px;height:40px;padding:0;border-radius:50%;background:white;font-size:22px;';
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      await fillTestData(number);
    } catch (error) {
      document.querySelector('#result').textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });
  document.querySelector('#test-case-buttons').append(button);
}

async function fillTestData(number = 1) {
  const response = await fetch(`/test-case-${number}.json`);
  if (!response.ok) throw new Error(`Не удалось загрузить пример ${number}.`);
  fillRequestData(await response.json());
}

function fillIntraTestData() { return fillTestData(2); }
function fillRumoTestData() { return fillTestData(3); }
