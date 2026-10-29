// Кот во время расчёта и кот, выезжающий по нажатию на хвост.
const calculationCat = document.createElement('dialog');
calculationCat.id = 'calculation-loader';
calculationCat.className = 'cat-loader';
calculationCat.setAttribute('aria-labelledby', 'cat-loading-text');
calculationCat.innerHTML = '<img src="/cat-calculation.gif" alt="Кот на золотой монетке моргает и машет лапкой" width="288" height="384"><p id="cat-loading-text">Считаем…</p>';
calculationCat.addEventListener('cancel', event => event.preventDefault());
document.body.append(calculationCat);

function showCalculationCat() {
  calculationCat.showModal();
  return new Promise(resolve => setTimeout(resolve, 2000));
}

function hideCalculationCat() {
  calculationCat.close();
}

const catTail = document.createElement('button');
catTail.type = 'button';
catTail.className = 'cat-tail-button';
catTail.setAttribute('aria-label', 'Показать кота');
catTail.setAttribute('aria-controls', 'cat-slide');
catTail.setAttribute('aria-expanded', 'false');
catTail.title = 'Показать кота';
catTail.innerHTML = '<span class="cat-tail" aria-hidden="true"></span>';

const catSlide = document.createElement('aside');
catSlide.id = 'cat-slide';
catSlide.className = 'cat-slide';
catSlide.setAttribute('role', 'dialog');
catSlide.setAttribute('aria-label', 'Кот на компьютерной мышке');
catSlide.setAttribute('aria-hidden', 'true');
catSlide.inert = true;
catSlide.innerHTML = '<button class="cat-picture" type="button" aria-label="Спрятать кота"><img src="/cat-peek.jpg" alt="Кот на компьютерной мышке" width="280" height="365"></button><button class="cat-close" type="button" aria-label="Закрыть кота">×</button>';
document.body.append(catTail, catSlide);

function toggleCat() {
  const open = !catSlide.classList.contains('is-open');
  catSlide.classList.toggle('is-open', open);
  catSlide.setAttribute('aria-hidden', String(!open));
  catSlide.inert = !open;
  catTail.setAttribute('aria-expanded', String(open));
  catTail.classList.toggle('cat-is-open', open);
  catTail.tabIndex = open ? -1 : 0;
  if (open) catSlide.querySelector('.cat-close').focus({ preventScroll: true });
  else catTail.focus({ preventScroll: true });
}

catTail.addEventListener('click', toggleCat);
catSlide.querySelector('.cat-picture').addEventListener('click', toggleCat);
catSlide.querySelector('.cat-close').addEventListener('click', toggleCat);
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && catSlide.classList.contains('is-open') && !calculationCat.open) {
    event.preventDefault();
    toggleCat();
  }
});
