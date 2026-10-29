// Временные примеры: каждый файл test-case-N.json содержит только исходный запрос.
// Чтобы убрать кнопку, удалите подключение test-data.js из index.html.
const exampleButton = document.createElement('button');
exampleButton.type = 'button';
exampleButton.textContent = '🐞';
exampleButton.title = 'Пример 1: Г-0029';
exampleButton.setAttribute('aria-label', exampleButton.title);
exampleButton.dataset.testCase = 1;
exampleButton.style.cssText = 'width:40px;height:40px;padding:0;border-radius:50%;background:white;font-size:22px;';
exampleButton.addEventListener('click', async () => {
  exampleButton.disabled = true;
  try {
    await fillTestData();
  } catch (error) {
    document.querySelector('#result').textContent = error.message;
  } finally {
    exampleButton.disabled = false;
  }
});
document.querySelector('#test-case-buttons').append(exampleButton);

async function fillTestData(number = 1) {
  const response = await fetch(`/test-case-${number}.json`);
  if (!response.ok) throw new Error(`Не удалось загрузить пример ${number}.`);
  fillRequestData(await response.json());
}
