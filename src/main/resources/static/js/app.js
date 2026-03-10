/**
 * RAG Search - Frontend JavaScript
 * Handles file upload, drag & drop, polling, and UI interactions.
 */

document.addEventListener('DOMContentLoaded', () => {
    initDropzone();
    initSearchForm();
    initDocumentListAutoRefresh();
});

let documentListRefreshIntervalId = null;

// ==========================================
// File Upload & Drag and Drop
// ==========================================

function initDropzone() {
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('file-input');

    if (!dropzone || !fileInput) return;

    // Click to browse
    dropzone.addEventListener('click', () => fileInput.click());

    // File selected via input
    fileInput.addEventListener('change', (e) => {
        handleFiles(e.target.files);
        fileInput.value = ''; // Reset to allow re-upload of same file
    });

    // Drag & Drop events
    ['dragenter', 'dragover'].forEach(event => {
        dropzone.addEventListener(event, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add('dragover');
        });
    });

    ['dragleave', 'drop'].forEach(event => {
        dropzone.addEventListener(event, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove('dragover');
        });
    });

    dropzone.addEventListener('drop', (e) => {
        const files = e.dataTransfer.files;
        handleFiles(files);
    });
}

function handleFiles(files) {
    const statusArea = document.getElementById('upload-status');
    if (!statusArea) return;

    statusArea.style.display = 'block';

    Array.from(files).forEach(file => {
        uploadFile(file, statusArea);
    });
}

function uploadFile(file, statusArea) {
    const itemId = 'upload-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);

    // Create upload status item
    const uploadItem = document.createElement('div');
    uploadItem.className = 'upload-item';
    uploadItem.id = itemId;
    uploadItem.innerHTML = `
        <div class="upload-item-icon uploading">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
        </div>
        <div class="upload-item-info">
            <div class="upload-item-name">${escapeHtml(file.name)}</div>
            <div class="upload-item-status">Feltöltés...</div>
            <div class="upload-progress">
                <div class="upload-progress-bar" style="width: 0%"></div>
            </div>
        </div>
    `;
    statusArea.prepend(uploadItem);

    // Upload via XMLHttpRequest for progress tracking
    const formData = new FormData();
    formData.append('file', file);

    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            const percent = Math.round((e.loaded / e.total) * 100);
            const progressBar = uploadItem.querySelector('.upload-progress-bar');
            if (progressBar) {
                progressBar.style.width = percent + '%';
            }
        }
    });

    xhr.addEventListener('load', () => {
        const icon = uploadItem.querySelector('.upload-item-icon');
        const statusText = uploadItem.querySelector('.upload-item-status');
        const progressDiv = uploadItem.querySelector('.upload-progress');

        if (xhr.status === 200) {
            const response = JSON.parse(xhr.responseText);

            icon.className = 'upload-item-icon success';
            icon.innerHTML = `
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="20 6 9 17 4 12"/>
                </svg>
            `;
            statusText.textContent = response.message || 'Feltöltve, feldolgozás folyamatban...';
            if (progressDiv) progressDiv.style.display = 'none';

            // Start polling for processing status
            if (response.documentId) {
                pollDocumentStatus(response.documentId, statusText);
            }
        } else {
            let errorMessage = 'Hiba a feltöltés során';
            try {
                const response = JSON.parse(xhr.responseText);
                errorMessage = response.message || errorMessage;
            } catch (e) { /* ignore */ }

            icon.className = 'upload-item-icon error';
            icon.innerHTML = `
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
            `;
            statusText.textContent = errorMessage;
            statusText.style.color = 'var(--danger)';
            if (progressDiv) progressDiv.style.display = 'none';
        }
    });

    xhr.addEventListener('error', () => {
        const icon = uploadItem.querySelector('.upload-item-icon');
        const statusText = uploadItem.querySelector('.upload-item-status');
        const progressDiv = uploadItem.querySelector('.upload-progress');

        icon.className = 'upload-item-icon error';
        statusText.textContent = 'Hálózati hiba történt';
        statusText.style.color = 'var(--danger)';
        if (progressDiv) progressDiv.style.display = 'none';
    });

    xhr.open('POST', '/documents/upload');
    xhr.send(formData);
}

/**
 * Poll document processing status until complete or failed.
 */
function pollDocumentStatus(documentId, statusElement) {
    const maxAttempts = 120; // 10 minutes max (5s intervals)
    let attempts = 0;

    const interval = setInterval(() => {
        attempts++;

        if (attempts >= maxAttempts) {
            clearInterval(interval);
            statusElement.textContent = 'Időtúllépés - ellenőrizd az állapotot manuálisan';
            statusElement.style.color = 'var(--warning)';
            return;
        }

        fetch(`/documents/${documentId}/status`)
            .then(response => response.json())
            .then(doc => {
                switch (doc.status) {
                    case 'COMPLETED':
                        clearInterval(interval);
                        statusElement.textContent = `Kész! ${doc.chunkCount || 0} chunk létrehozva.`;
                        statusElement.style.color = 'var(--success)';
                        break;
                    case 'FAILED':
                        clearInterval(interval);
                        statusElement.textContent = `Sikertelen: ${doc.errorMessage || 'Ismeretlen hiba'}`;
                        statusElement.style.color = 'var(--danger)';
                        break;
                    case 'PROCESSING':
                        if (doc.chunkCount && doc.chunkCount > 0) {
                            statusElement.textContent = `Feldolgozás folyamatban... (${doc.chunkCount} chunk kész)`;
                        } else {
                            statusElement.textContent = 'Feldolgozás folyamatban...';
                        }
                        break;
                    case 'PENDING':
                        statusElement.textContent = 'Várakozás a feldolgozásra...';
                        break;
                }
            })
            .catch(err => {
                console.error('Status poll error:', err);
            });

    }, 5000); // Poll every 5 seconds
}

// ==========================================
// Upload Page - Live Document List
// ==========================================

function initDocumentListAutoRefresh() {
    const tableBody = document.getElementById('document-list-body');
    if (!tableBody) return;

    if (documentListRefreshIntervalId) {
        clearInterval(documentListRefreshIntervalId);
    }

    documentListRefreshIntervalId = setInterval(() => {
        // Skip background tabs to reduce unnecessary server load.
        if (document.hidden) return;
        refreshDocumentList();
    }, 5000);
}

function refreshDocumentList() {
    const tableBody = document.getElementById('document-list-body');
    if (!tableBody) return;

    if (window.htmx && typeof window.htmx.ajax === 'function') {
        window.htmx.ajax('GET', '/documents/list', {
            target: '#document-list-body',
            swap: 'innerHTML'
        });
    }
}

// ==========================================
// Search Form
// ==========================================

function initSearchForm() {
    const form = document.getElementById('search-form');
    const queryInput = document.getElementById('search-query');

    // Handle Enter key (Shift+Enter for newline)
    if (form && queryInput) {
        queryInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                htmx.trigger(form, 'submit');
            }
        });

        // Auto-resize textarea
        queryInput.addEventListener('input', function() {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 200) + 'px';
        });
    }

    // Re-initialize Lucide icons after HTMX swap on every page.
    document.body.addEventListener('htmx:afterSwap', () => {
        if (typeof lucide !== 'undefined') {
            lucide.createIcons();
        }
    });
}

// ==========================================
// Utilities
// ==========================================

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
