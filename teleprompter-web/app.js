/**
 * Телесуфлер PRO — Mobile Web App & PWA
 * - Front camera preview + MediaRecorder video recording
 * - Auto-scrolling teleprompter docked right beneath camera punch-hole / notch
 * - Native offline DOCX parser (using browser DecompressionStream & DOMParser)
 * - Fine-grained speed, font-size, height, opacity, and mirroring controls
 */

(function () {
  'use strict';

  // State
  const state = {
    cameraStream: null,
    mediaRecorder: null,
    recordedChunks: [],
    recordStartTime: null,
    recordTimerInterval: null,
    facingMode: 'user', // 'user' for front camera, 'environment' for back

    // Prompter state
    isPlaying: false,
    scrollSpeed: 3.0, // multiplier 1 - 10
    fontSize: 22,
    overlayHeight: 220,
    overlayOpacity: 75,
    isMirrored: false,
    lastFrameTime: null,
    scrollAccumulator: 0,
    animFrameId: null
  };

  // DOM Elements
  const cameraVideo = document.getElementById('cameraVideo');
  const cameraPlaceholder = document.getElementById('cameraPlaceholder');
  const btnStartCamera = document.getElementById('btnStartCamera');
  const btnSwitchCamera = document.getElementById('btnSwitchCamera');
  const recBadge = document.getElementById('recBadge');
  const recTimer = document.getElementById('recTimer');

  const prompterOverlay = document.getElementById('prompterOverlay');
  const prompterScroller = document.getElementById('prompterScroller');
  const prompterContent = document.getElementById('prompterContent');
  const docTitle = document.getElementById('docTitle');
  const speedValueDisplay = document.getElementById('speedValueDisplay');
  const speedSettingDisplay = document.getElementById('speedSettingDisplay');
  const fontSizeDisplay = document.getElementById('fontSizeDisplay');
  const overlayHeightDisplay = document.getElementById('overlayHeightDisplay');
  const opacityDisplay = document.getElementById('opacityDisplay');

  // Controls
  const btnPlayPauseScroll = document.getElementById('btnPlayPauseScroll');
  const playPauseLabel = document.getElementById('playPauseLabel');
  const playIcon = document.getElementById('playIcon');
  const pauseIcon = document.getElementById('pauseIcon');
  const btnResetScroll = document.getElementById('btnResetScroll');
  const btnSpeedDown = document.getElementById('btnSpeedDown');
  const btnSpeedUp = document.getElementById('btnSpeedUp');
  const btnTogglePrompterExpand = document.getElementById('btnTogglePrompterExpand');

  // HUD
  const docxFileInput = document.getElementById('docxFileInput');
  const btnRecordVideo = document.getElementById('btnRecordVideo');
  const recordLabel = document.getElementById('recordLabel');
  const btnToggleSettings = document.getElementById('btnToggleSettings');

  // Drawer
  const settingsDrawer = document.getElementById('settingsDrawer');
  const btnCloseSettings = document.getElementById('btnCloseSettings');
  const speedSlider = document.getElementById('speedSlider');
  const fontSizeSlider = document.getElementById('fontSizeSlider');
  const overlayHeightSlider = document.getElementById('overlayHeightSlider');
  const opacitySlider = document.getElementById('opacitySlider');
  const toggleBacklight = document.getElementById('toggleBacklight');
  const backlightFrame = document.getElementById('backlightFrame');
  const manualTextEdit = document.getElementById('manualTextEdit');
  const btnApplyManualText = document.getElementById('btnApplyManualText');

  // Modal
  const videoModal = document.getElementById('videoModal');
  const recordedPreviewVideo = document.getElementById('recordedPreviewVideo');
  const btnDownloadVideo = document.getElementById('btnDownloadVideo');
  const btnCloseModal = document.getElementById('btnCloseModal');
  const toast = document.getElementById('toast');

  /* ==========================================================================
     Toast Notifications
     ========================================================================== */
  let toastTimeout = null;
  function showToast(message) {
    if (toastTimeout) clearTimeout(toastTimeout);
    toast.textContent = message;
    toast.classList.remove('hidden');
    toastTimeout = setTimeout(() => {
      toast.classList.add('hidden');
    }, 2800);
  }

  /* ==========================================================================
     Camera & Video Recording Engine
     ========================================================================== */
  async function initCamera() {
    stopCamera();
    try {
      const constraints = {
        video: {
          facingMode: state.facingMode,
          width: { ideal: 1920 },
          height: { ideal: 1080 }
        },
        audio: true
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      state.cameraStream = stream;
      cameraVideo.srcObject = stream;
      await cameraVideo.play();

      cameraPlaceholder.classList.add('hidden');
      showToast('Фронтальная камера готова');
    } catch (err) {
      console.warn('Direct camera access failed, trying video without audio:', err);
      try {
        // Retry with video only (some devices block mic or have no mic)
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: state.facingMode }
        });
        state.cameraStream = stream;
        cameraVideo.srcObject = stream;
        await cameraVideo.play();
        cameraPlaceholder.classList.add('hidden');
        showToast('Камера включена (без микрофона)');
      } catch (videoErr) {
        console.error('Camera error:', videoErr);
        // If camera permission blocked or on headless simulator, create dynamic animated canvas test stream
        setupMockCameraStream();
        showToast('Используется тестовый видеопоток');
      }
    }
  }

  function setupMockCameraStream() {
    // Generate an animated video stream so recording and preview work even in browsers without physical cameras
    const canvas = document.createElement('canvas');
    canvas.width = 720;
    canvas.height = 1280;
    const ctx = canvas.getContext('2d');
    let frame = 0;

    function drawMock() {
      frame++;
      // Background gradient
      const grad = ctx.createLinearGradient(0, 0, 0, 1280);
      grad.addColorStop(0, '#111827');
      grad.addColorStop(1, '#1f2937');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, 720, 1280);

      // Grid
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
      ctx.lineWidth = 1;
      for (let x = 0; x < 720; x += 40) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, 1280);
        ctx.stroke();
      }
      for (let y = 0; y < 1280; y += 40) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(720, y);
        ctx.stroke();
      }

      // Simulated speaker silhouette
      ctx.fillStyle = '#374151';
      ctx.beginPath();
      ctx.arc(360, 520, 120, 0, Math.PI * 2);
      ctx.fill();
      ctx.beginPath();
      ctx.ellipse(360, 850, 220, 200, 0, 0, Math.PI);
      ctx.fill();

      // Timestamp & indicator
      ctx.fillStyle = '#60a5fa';
      ctx.font = '24px Outfit, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('СИМУЛЯТОР ФРОНТАЛЬНОЙ КАМЕРЫ', 360, 320);
      ctx.fillStyle = '#9ca3af';
      ctx.font = '18px monospace';
      ctx.fillText(new Date().toLocaleTimeString() + ' | FPS: 30', 360, 360);

      requestAnimationFrame(drawMock);
    }
    drawMock();

    const stream = canvas.captureStream(30);
    // Add silent audio track using Web Audio
    try {
      const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = audioCtx.createOscillator();
      const dest = audioCtx.createMediaStreamDestination();
      osc.connect(dest);
      osc.start();
      dest.stream.getAudioTracks().forEach(t => stream.addTrack(t));
    } catch(e) {}

    state.cameraStream = stream;
    cameraVideo.srcObject = stream;
    cameraVideo.play();
    cameraPlaceholder.classList.add('hidden');
  }

  function stopCamera() {
    if (state.cameraStream) {
      state.cameraStream.getTracks().forEach(track => track.stop());
      state.cameraStream = null;
    }
  }

  function switchCamera() {
    state.facingMode = state.facingMode === 'user' ? 'environment' : 'user';
    initCamera();
  }

  /* ==========================================================================
     Video Recording
     ========================================================================== */
  function toggleRecording() {
    if (state.mediaRecorder && state.mediaRecorder.state === 'recording') {
      stopRecording();
    } else {
      startRecording();
    }
  }

  function startRecording() {
    if (!state.cameraStream) {
      showToast('Сначала включите камеру');
      initCamera();
      return;
    }

    state.recordedChunks = [];
    let options = { mimeType: 'video/webm;codecs=vp8,opus' };
    if (!MediaRecorder.isTypeSupported(options.mimeType)) {
      options = { mimeType: 'video/webm' };
      if (!MediaRecorder.isTypeSupported(options.mimeType)) {
        options = { mimeType: 'video/mp4' };
        if (!MediaRecorder.isTypeSupported(options.mimeType)) {
          options = {};
        }
      }
    }

    try {
      state.mediaRecorder = new MediaRecorder(state.cameraStream, options);
    } catch (e) {
      state.mediaRecorder = new MediaRecorder(state.cameraStream);
    }

    state.mediaRecorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        state.recordedChunks.push(event.data);
      }
    };

    state.mediaRecorder.onstop = () => {
      const mime = state.mediaRecorder.mimeType || 'video/webm';
      const blob = new Blob(state.recordedChunks, { type: mime });
      const videoURL = URL.createObjectURL(blob);
      recordedPreviewVideo.src = videoURL;
      btnDownloadVideo.href = videoURL;
      btnDownloadVideo.download = `teleprompter_rec_${Date.now()}.webm`;
      videoModal.classList.remove('hidden');
    };

    state.mediaRecorder.start(1000); // 1-sec chunks
    state.recordStartTime = Date.now();
    updateRecTimer();
    state.recordTimerInterval = setInterval(updateRecTimer, 1000);

    // Visual indicators
    recBadge.classList.remove('hidden');
    btnRecordVideo.classList.add('recording');
    recordLabel.textContent = 'Стоп';

    // Auto-start prompter if paused
    if (!state.isPlaying) {
      togglePrompterPlay(true);
    }

    showToast('Запись видео начата');
  }

  function stopRecording() {
    if (state.mediaRecorder && state.mediaRecorder.state !== 'inactive') {
      state.mediaRecorder.stop();
    }
    clearInterval(state.recordTimerInterval);
    recBadge.classList.add('hidden');
    btnRecordVideo.classList.remove('recording');
    recordLabel.textContent = 'Запись';

    // Pause prompter when video finishes
    if (state.isPlaying) {
      togglePrompterPlay(false);
    }

    showToast('Запись завершена');
  }

  function updateRecTimer() {
    if (!state.recordStartTime) return;
    const elapsed = Math.floor((Date.now() - state.recordStartTime) / 1000);
    const mins = String(Math.floor(elapsed / 60)).padStart(2, '0');
    const secs = String(elapsed % 60).padStart(2, '0');
    recTimer.textContent = `${mins}:${secs}`;
  }

  /* ==========================================================================
     Prompter Animation Engine (Smooth sub-pixel continuous scrolling)
     ========================================================================== */
  function togglePrompterPlay(forceState) {
    state.isPlaying = typeof forceState === 'boolean' ? forceState : !state.isPlaying;

    if (state.isPlaying) {
      state.lastFrameTime = performance.now();
      playIcon.classList.add('hidden');
      pauseIcon.classList.remove('hidden');
      playPauseLabel.textContent = 'Пауза';
      btnPlayPauseScroll.classList.add('active');
      state.animFrameId = requestAnimationFrame(scrollStep);
    } else {
      playIcon.classList.remove('hidden');
      pauseIcon.classList.add('hidden');
      playPauseLabel.textContent = 'Старт';
      btnPlayPauseScroll.classList.remove('active');
      if (state.animFrameId) {
        cancelAnimationFrame(state.animFrameId);
        state.animFrameId = null;
      }
    }
  }

  function scrollStep(timestamp) {
    if (!state.isPlaying) return;

    if (!state.lastFrameTime) {
      state.lastFrameTime = timestamp;
    }
    const dt = (timestamp - state.lastFrameTime) / 1000;
    state.lastFrameTime = timestamp;

    // Base speed: 20 pixels per second multiplied by scrollSpeed factor
    // speed 1 = 20px/s, speed 3 = 60px/s, speed 10 = 200px/s
    const pixelsPerSecond = 20 * state.scrollSpeed;
    state.scrollAccumulator += pixelsPerSecond * dt;

    if (state.scrollAccumulator >= 1) {
      const pxToScroll = Math.floor(state.scrollAccumulator);
      prompterScroller.scrollTop += pxToScroll;
      state.scrollAccumulator -= pxToScroll;
    }

    // Check if reached the end
    const maxScroll = prompterScroller.scrollHeight - prompterScroller.clientHeight;
    if (prompterScroller.scrollTop >= maxScroll && maxScroll > 0) {
      togglePrompterPlay(false);
      showToast('Конец сценария');
      return;
    }

    state.animFrameId = requestAnimationFrame(scrollStep);
  }

  function resetPrompterScroll() {
    prompterScroller.scrollTop = 0;
    state.scrollAccumulator = 0;
    showToast('Перемотано в начало');
  }

  function adjustSpeed(delta) {
    let newSpeed = Math.round((state.scrollSpeed + delta) * 2) / 2;
    newSpeed = Math.max(1, Math.min(10, newSpeed));
    setSpeed(newSpeed);
  }

  function setSpeed(val) {
    state.scrollSpeed = parseFloat(val);
    speedValueDisplay.textContent = `${state.scrollSpeed}×`;
    speedSettingDisplay.textContent = `${state.scrollSpeed}×`;
    speedSlider.value = state.scrollSpeed;
  }

  function setFontSize(size) {
    state.fontSize = parseInt(size, 10);
    prompterContent.style.fontSize = `${state.fontSize}px`;
    fontSizeDisplay.textContent = `${state.fontSize} px`;
    fontSizeSlider.value = state.fontSize;
  }

  function setOverlayHeight(h) {
    state.overlayHeight = parseInt(h, 10);
    prompterScroller.style.height = `${state.overlayHeight}px`;
    overlayHeightDisplay.textContent = `${state.overlayHeight} px`;
    overlayHeightSlider.value = state.overlayHeight;
  }

  function setOverlayOpacity(op) {
    state.overlayOpacity = parseInt(op, 10);
    const alpha = (state.overlayOpacity / 100).toFixed(2);
    prompterOverlay.style.background = `linear-gradient(180deg, rgba(6, 8, 14, ${alpha}) 0%, rgba(10, 12, 20, ${alpha * 0.9}) 80%, rgba(10, 12, 20, ${alpha * 0.4}) 100%)`;
    opacityDisplay.textContent = `${state.overlayOpacity}%`;
    opacitySlider.value = state.overlayOpacity;
  }

  function toggleMirrorMode(enable) {
    state.isMirrored = enable;
    if (enable) {
      prompterContent.classList.add('mirrored');
    } else {
      prompterContent.classList.remove('mirrored');
    }
  }

  /* ==========================================================================
     DOCX Reader Engine (Native Offline ZIP + XML Parser)
     ========================================================================== */
  async function parseDocxFile(file) {
    showToast(`Загрузка «${file.name}»...`);
    try {
      const buffer = await file.arrayBuffer();
      const documentXml = await extractDocumentXmlFromZip(buffer);

      if (!documentXml) {
        throw new Error('Файл document.xml не найден в архиве DOCX');
      }

      // Parse XML
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(documentXml, 'application/xml');

      // Check parse errors
      const parseError = xmlDoc.querySelector('parsererror');
      if (parseError) {
        throw new Error('Ошибка разбора XML документа: ' + parseError.textContent);
      }

      // Extract paragraphs: <w:p>
      const paragraphs = xmlDoc.getElementsByTagNameNS('*', 'p');
      const lines = [];

      for (let i = 0; i < paragraphs.length; i++) {
        const p = paragraphs[i];
        const textNodes = p.getElementsByTagNameNS('*', 't');
        let pText = '';
        for (let j = 0; j < textNodes.length; j++) {
          pText += textNodes[j].textContent;
        }
        if (pText.trim().length > 0) {
          lines.push(pText.trim());
        }
      }

      if (lines.length === 0) {
        // Fallback: extract any text nodes
        const allTexts = xmlDoc.getElementsByTagNameNS('*', 't');
        let full = '';
        for (let k = 0; k < allTexts.length; k++) {
          full += allTexts[k].textContent + ' ';
        }
        if (full.trim().length > 0) {
          lines.push(full.trim());
        } else {
          throw new Error('Текст в документе DOCX не найден');
        }
      }

      // Render extracted text
      renderTextToPrompter(lines, file.name);
      showToast(`Импортировано ${lines.length} параграфов!`);
    } catch (err) {
      console.error('Docx parse error:', err);
      showToast('Ошибка чтения DOCX: ' + err.message);
    }
  }

  /**
   * Native ZIP extractor to retrieve 'word/document.xml' from DOCX array buffer.
   * Leverages browser native DecompressionStream('deflate-raw').
   */
  async function extractDocumentXmlFromZip(arrayBuffer) {
    const view = new DataView(arrayBuffer);
    const bytes = new Uint8Array(arrayBuffer);
    let offset = 0;
    const len = bytes.length;

    // Scan for Local File Headers (Signature: 0x04034b50 -> 'PK\x03\x04')
    while (offset < len - 30) {
      if (
        bytes[offset] === 0x50 &&
        bytes[offset + 1] === 0x4b &&
        bytes[offset + 2] === 0x03 &&
        bytes[offset + 3] === 0x04
      ) {
        const compressionMethod = view.getUint16(offset + 8, true);
        const compressedSize = view.getUint32(offset + 18, true);
        const uncompressedSize = view.getUint32(offset + 22, true);
        const fileNameLen = view.getUint16(offset + 26, true);
        const extraFieldLen = view.getUint16(offset + 28, true);

        const fileNameBytes = bytes.subarray(offset + 30, offset + 30 + fileNameLen);
        const rawFileName = new TextDecoder('utf-8').decode(fileNameBytes);
        const fileName = rawFileName.replace(/\\/g, '/');

        const dataOffset = offset + 30 + fileNameLen + extraFieldLen;

        if (fileName === 'word/document.xml') {
          // If uncompressed
          if (compressionMethod === 0) {
            const xmlBytes = bytes.subarray(dataOffset, dataOffset + uncompressedSize);
            return new TextDecoder('utf-8').decode(xmlBytes);
          }
          // If Deflate compressed
          if (compressionMethod === 8) {
            const compressedSlice = bytes.subarray(dataOffset, dataOffset + compressedSize);
            // Decompress via browser native DecompressionStream
            if (typeof DecompressionStream !== 'undefined') {
              const stream = new ReadableStream({
                start(controller) {
                  controller.enqueue(compressedSlice);
                  controller.close();
                }
              }).pipeThrough(new DecompressionStream('deflate-raw'));

              const decompressedResponse = await new Response(stream).arrayBuffer();
              return new TextDecoder('utf-8').decode(decompressedResponse);
            }
          }
        }

        // Advance to next header
        if (compressedSize > 0) {
          offset = dataOffset + compressedSize;
        } else {
          offset += 30 + fileNameLen + extraFieldLen;
        }
      } else {
        offset++;
      }
    }

    return null;
  }

  function renderTextToPrompter(paragraphs, title = '') {
    prompterContent.innerHTML = '';
    paragraphs.forEach((pText, idx) => {
      const p = document.createElement('p');
      p.className = 'script-p';
      if (idx === 0) p.classList.add('lead-p');
      p.textContent = pText;
      prompterContent.appendChild(p);
    });

    if (title && docTitle) {
      docTitle.textContent = title;
    }

    // Reset scroll to top
    prompterScroller.scrollTop = 0;
    state.scrollAccumulator = 0;

    // Update manual text edit textarea as well
    manualTextEdit.value = paragraphs.join('\n\n');
  }

  /* ==========================================================================
     Event Listeners
     ========================================================================== */
  btnStartCamera.addEventListener('click', () => initCamera());
  btnSwitchCamera.addEventListener('click', () => switchCamera());

  btnPlayPauseScroll.addEventListener('click', () => togglePrompterPlay());
  btnResetScroll.addEventListener('click', () => resetPrompterScroll());

  btnSpeedDown.addEventListener('click', () => adjustSpeed(-0.5));
  btnSpeedUp.addEventListener('click', () => adjustSpeed(+0.5));

  btnRecordVideo.addEventListener('click', () => toggleRecording());

  docxFileInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      parseDocxFile(file);
    }
  });

  // Tap on the prompter to pause/play
  prompterScroller.addEventListener('click', (e) => {
    // Only toggle if not dragging
    togglePrompterPlay();
  });

  // Quick Camera & prompter actions
  const btnSwitchCameraQuick = document.getElementById('btnSwitchCameraQuick');
  if (btnSwitchCameraQuick) {
    btnSwitchCameraQuick.addEventListener('click', () => switchCamera());
  }

  // Quick expand/collapse (if present)
  if (btnTogglePrompterExpand) {
    btnTogglePrompterExpand.addEventListener('click', () => {
      prompterOverlay.classList.toggle('compact');
    });
  }

  // Settings Drawer
  btnToggleSettings.addEventListener('click', () => {
    settingsDrawer.classList.remove('hidden');
  });

  btnCloseSettings.addEventListener('click', () => {
    settingsDrawer.classList.add('hidden');
  });

  speedSlider.addEventListener('input', (e) => setSpeed(e.target.value));
  fontSizeSlider.addEventListener('input', (e) => setFontSize(e.target.value));
  overlayHeightSlider.addEventListener('input', (e) => setOverlayHeight(e.target.value));
  opacitySlider.addEventListener('input', (e) => setOverlayOpacity(e.target.value));

  if (toggleBacklight && backlightFrame) {
    toggleBacklight.addEventListener('change', (e) => {
      backlightFrame.classList.toggle('hidden', !e.target.checked);
    });
  }

  btnApplyManualText.addEventListener('click', () => {
    const text = manualTextEdit.value.trim();
    if (text) {
      const lines = text.split(/\n+/).filter(line => line.trim().length > 0);
      renderTextToPrompter(lines, 'Пользовательский текст');
      settingsDrawer.classList.add('hidden');
      showToast('Текст обновлен');
    }
  });

  btnCloseModal.addEventListener('click', () => {
    videoModal.classList.add('hidden');
    recordedPreviewVideo.pause();
  });

  // Keyboard shortcuts (Space = play/pause, R = record, Up/Down = speed)
  window.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'TEXTAREA' || e.target.tagName === 'INPUT') return;

    if (e.code === 'Space') {
      e.preventDefault();
      togglePrompterPlay();
    } else if (e.code === 'KeyR') {
      toggleRecording();
    } else if (e.code === 'ArrowUp') {
      adjustSpeed(+0.5);
    } else if (e.code === 'ArrowDown') {
      adjustSpeed(-0.5);
    }
  });

  // Auto-init camera on load
  window.addEventListener('DOMContentLoaded', () => {
    // Initialize defaults
    setSpeed(3.0);
    setFontSize(22);
    setOverlayHeight(220);
    setOverlayOpacity(75);

    // Try starting camera right away
    initCamera();
  });

})();
