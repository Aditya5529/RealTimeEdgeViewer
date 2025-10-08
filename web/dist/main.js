"use strict";
const canvas = document.getElementById('cv');
const ctx = canvas.getContext('2d');
const stats = document.getElementById('stats');
async function init() {
    const img = new Image();
    img.src = 'assets/sample.png';
    await img.decode(); // now inside async fn (no top-level await)
    const W = img.naturalWidth, H = img.naturalHeight;
    canvas.width = W;
    canvas.height = H;
    stats.textContent = `Resolution: ${W}×${H} | FPS: —`;
    let frameCount = 0; // avoid Window.frames name clash
    let lastT = performance.now();
    function loop(t) {
        ctx.drawImage(img, 0, 0, W, H);
        frameCount++;
        if (t - lastT >= 1000) {
            stats.textContent = `Resolution: ${W}×${H} | FPS: ${frameCount}`;
            frameCount = 0;
            lastT = t;
        }
        requestAnimationFrame(loop);
    }
    requestAnimationFrame(loop);
}
init();
