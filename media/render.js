// Renders the page banners from banners.html (run from this folder; needs playwright and the captures).
const {chromium} = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({viewport: {width: 1800, height: 2000}});
  await p.goto('file://' + require('path').resolve('banners.html'));
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(500);
  for (const id of ['hero', 'header', 'donate']) {
    await (await p.$('#' + id)).screenshot({path: id + '.png'});
  }
  await b.close();
})();
