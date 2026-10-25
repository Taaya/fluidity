function buildShareProgress(items, now = new Date()) {
  // Seven local calendar days including today, bounded by the current instant.
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - 6);
  let practiced = 0;
  let used = 0;
  const words = [];
  for (const item of items) {
    let practicedInPeriod = false;
    let usedInPeriod = false;
    let latestTime = -Infinity;
    for (const attempt of item.practiceHistory || []) {
      if (!attempt || !["used", "needs-practice"].includes(attempt.result)) continue;
      const time = Date.parse(attempt.practicedAt);
      if (time < start.getTime() || time > now.getTime()) continue;
      practicedInPeriod = true;
      usedInPeriod ||= attempt.result === "used";
      latestTime = Math.max(latestTime, time);
    }
    // Items practiced before detailed history was introduced still retain their
    // latest practice date and outcome. Include that real snapshot in the period.
    const legacyResult = item.legacyLastResult || (item.status === "confident" ? "used"
      : item.status === "needs-practice" ? "needs-practice" : null);
    if (!practicedInPeriod && !item.practiceHistory?.length && item.attempts > 0 && legacyResult) {
      const time = Date.parse(item.lastPracticedAt);
      if (time >= start.getTime() && time <= now.getTime()) {
        practicedInPeriod = true;
        usedInPeriod = legacyResult === "used";
        latestTime = time;
      }
    }
    if (practicedInPeriod) {
      practiced += 1;
      used += Number(usedInPeriod);
      if (typeof item.text === "string" && item.text.trim()) {
        words.push({ text: item.text.trim(), practicedAt: latestTime });
      }
    }
  }
  const formatter = new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" });
  const period = `${formatter.format(start)} - ${formatter.format(now)}`;
  const noun = practiced === 1 ? "word or phrase" : "words and phrases";
  const text = `My English practice | ${period}\n\nI practiced ${practiced} ${noun} aloud.\nI managed to use ${used} of ${practiced} in context at least once.\n\nBased on my self-assessment.\nWordswith`;
  words.sort((first, second) => second.practicedAt - first.practicedAt);
  return { practiced, used, period, text, words: words.map(item => item.text) };
}

async function createProgressImage(progress) {
  await Promise.all([document.fonts.load('500 80px Fraunces'), document.fonts.load('30px "DM Sans"')]);
  const canvas = document.createElement('canvas');
  canvas.width = 1080;
  const ctx = canvas.getContext('2d');
  // Measure rows before sizing the canvas, which resets the drawing context.
  ctx.font = '30px "DM Sans", sans-serif';
  const chips = [];
  let x = 56;
  let y = 634;
  for (const word of progress.words) {
    let label = word;
    if (ctx.measureText(label).width > 916) {
      const letters = Array.from(label);
      while (letters.length && ctx.measureText(`${letters.join('')}...`).width > 916) letters.pop();
      label = `${letters.join('')}...`;
    }
    const width = Math.ceil(ctx.measureText(label).width) + 48;
    if (x + width > 1024) { x = 56; y += 82; }
    if (y + 64 > 1024) break;
    chips.push({ label, x, y, width });
    x += width + 16;
  }
  canvas.height = (chips.length ? chips[chips.length - 1].y + 64 : 580) + 56;
  const ink = '#292d28';
  const green = '#e6eedf';
  ctx.fillStyle = '#fbf7ed';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = ink;
  ctx.textBaseline = 'top';
  ctx.font = '500 80px Fraunces';
  ctx.fillStyle = '#234bcc';
  ctx.fillText('A little more', 56, 48);
  ctx.fillText('out loud.', 56, 158);
  ctx.font = '26px "DM Sans", sans-serif';
  ctx.fillStyle = ink;
  ctx.fillText('Wordswith / My progress', 56, 252);
  ctx.fillText(progress.period, 56, 286);

  ctx.fillStyle = green;
  ctx.fillRect(56, 356, 484, 224);
  ctx.fillStyle = '#faf0c7';
  ctx.fillRect(540, 356, 484, 224);
  ctx.strokeStyle = ink;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(56, 356); ctx.lineTo(1024, 356);
  ctx.moveTo(56, 580); ctx.lineTo(1024, 580);
  ctx.moveTo(540, 356); ctx.lineTo(540, 580);
  ctx.stroke();
  ctx.fillStyle = ink;
  ctx.font = '500 92px Fraunces';
  ctx.fillText(String(progress.practiced), 88, 386, 420);
  ctx.fillText(String(progress.used), 572, 386, 420);
  ctx.font = '26px "DM Sans", sans-serif';
  ctx.fillText('words practiced', 88, 516);
  ctx.fillText('words used in context', 572, 516);

  ctx.font = '30px "DM Sans", sans-serif';
  for (const { label, x, y, width } of chips) {
    ctx.fillStyle = green;
    ctx.beginPath();
    ctx.roundRect(x, y, width, 64, 32);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = ink;
    ctx.fillText(label, x + 24, y + 17);
  }
  return new Promise((resolve, reject) => canvas.toBlob(blob => {
    if (blob) resolve(blob);
    else reject(new Error('Could not create image'));
  }, 'image/png'));
}
