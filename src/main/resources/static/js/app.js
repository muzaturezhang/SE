(() => {
  'use strict';
  // Keep a keyboard user's place after a server-rendered validation failure.
  const errorSummary = document.querySelector('[role="alert"]:not([hidden])');
  if (errorSummary?.textContent.trim()) {
    errorSummary.setAttribute('tabindex', '-1');
    errorSummary.focus();
  }
  const menu = document.querySelector('#navigation-dialog');
  document.querySelector('[data-open-menu]')?.addEventListener('click', () => menu.showModal());
  document.querySelectorAll('[data-close-dialog]').forEach(button => {
    button.addEventListener('click', () => button.closest('dialog').close());
  });
  document.querySelectorAll('dialog').forEach(dialog => {
    dialog.addEventListener('click', event => {
      if (event.target !== dialog) return;
      const rect = dialog.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
    });
  });
  menu?.querySelectorAll('a').forEach(link => link.addEventListener('click', () => menu.close()));

  const form = document.querySelector('[data-stay-form]');
  const arrival = form?.querySelector('[name="arrival"]');
  const departure = form?.querySelector('[name="departure"]');
  const error = document.querySelector('[data-form-error]');
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = type => parts.find(item => item.type === type).value;
  const todayIso = arrival?.min || `${part('year')}-${part('month')}-${part('day')}`;
  const nextDay = value => {
    const date = new Date(`${value}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + 1);
    return date.toISOString().slice(0, 10);
  };
  const setDepartureMinimum = () => {
    departure.min = arrival.value ? nextDay(arrival.value) : nextDay(todayIso);
    departure.setCustomValidity('');
  };
  if (form) {
    arrival.min = todayIso;
    setDepartureMinimum();
    arrival.addEventListener('change', setDepartureMinimum);
    departure.addEventListener('input', () => departure.setCustomValidity(''));
    form.addEventListener('submit', event => {
      error.hidden = true;
      const invalidRange = departure.value <= arrival.value;
      departure.setCustomValidity(invalidRange ? '退房日期必须晚于入住日期。' : '');
      if (!form.reportValidity()) {
        event.preventDefault();
        error.textContent = '请检查入住日期、退房日期和房间数量。';
        error.hidden = false;
        return;
      }
    });
  }
  const category = document.querySelector('#room-category');
  category?.addEventListener('change', () => {
    const query = new URLSearchParams(location.search);
    query.set('roomType', category.value);
    location.assign(`${location.pathname}?${query.toString()}#collection`);
  });
  const detailDialog = document.querySelector('#room-detail-dialog');
  document.querySelectorAll('[data-room-detail]').forEach(button => {
    button.addEventListener('click', () => {
      const entry = button.closest('.room-entry');
      const source = entry.querySelector('img');
      const image = detailDialog.querySelector('#room-dialog-image');
      image.src = source.src;
      image.alt = source.alt;
      detailDialog.querySelector('#room-dialog-title').textContent = entry.querySelector('h3').textContent;
      detailDialog.querySelector('#room-dialog-description').textContent = entry.querySelector('.room-description').textContent;
      detailDialog.querySelector('#room-dialog-price').textContent = entry.querySelector('.room-rate').textContent;
      detailDialog.showModal();
    });
  });
  document.querySelector('[data-plan-stay]')?.addEventListener('click', () => {
    detailDialog.close();
    document.querySelector('#stay-search').scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    arrival.focus({ preventScroll: true });
  });
})();
