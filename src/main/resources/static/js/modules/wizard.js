window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.wizard = {
    methods: {
        randomizePrompt() {
            const prompts = [
                "Một bản pop ballad buồn bằng tiếng piano du dương, kể về câu chuyện tình cũ dưới mưa...",
                "Nhạc lofi hip hop thư giãn, nhịp điệu chậm rãi kết hợp tiếng mưa rơi ngoài cửa sổ...",
                "Nhạc pop sôi động kết hợp âm hưởng EDM hiện đại, mang năng lượng tích cực ngày mới...",
                "Nhạc cụ truyền thống sáo trúc hòa quyện nhạc điện tử EDM chillout huyền ảo...",
                "Nhạc rap nhẹ nhàng tâm trạng suy tư về cuộc sống và tương lai thành phố đêm đông...",
                "Nhạc Acoustic mộc mạc, guitar nhẹ nhàng sâu lắng viết cho buổi hoàng hôn bãi biển..."
            ];
            const randomIndex = Math.floor(Math.random() * prompts.length);
            this.generationForm.prompt = prompts[randomIndex];
            this.Toast.fire({ icon: 'info', title: 'Đã gợi ý ý tưởng ngẫu nhiên!' });
        },

        selectWizardAudience(audience) {
            if (this.musicBrief.audience === audience) return;
            this.musicBrief.audience = audience;
            this.musicBrief.useCase = '';
            this.musicBrief.venueStyle = '';
            this.musicBrief.platform = '';
            this.musicBrief.timeOfDay = '';
            if (audience === 'cafe') {
                this.musicBrief.vocalMode = 'instrumental';
                this.generationForm.instrumental = true;
                this.musicBrief.duration = '2 phút';
            } else {
                this.musicBrief.duration = '30 giây';
            }
            // Bối cảnh và các bước sau cần được chọn lại khi đổi đối tượng tạo nhạc.
            this.wizardFurthestStep = Math.min(this.wizardFurthestStep, 1);
        },

        createEmptyMusicBrief() {
            return {
                provider: '', audience: '', useCase: '', venueStyle: '', platform: '', timeOfDay: '',
                duration: '30 giây', genreId: null, genreName: '', mood: '', energy: 'Vừa phải',
                instruments: [], vocalMode: 'instrumental', vocalLanguage: 'Tiếng Việt', vocalGender: 'auto',
                lyrics: '', title: '', note: ''
            };
        },

        loadWizardBriefs() {
            try {
                const saved = JSON.parse(localStorage.getItem('music_wizard_briefs') || '{}');
                this.wizardBriefs = saved && typeof saved === 'object' ? saved : {};
            } catch (error) {
                this.wizardBriefs = {};
            }
        },

        saveWizardBriefForSong(songId) {
            if (!songId) return;
            const brief = JSON.parse(JSON.stringify(this.musicBrief));
            this.$set(this.wizardBriefs, String(songId), brief);
            localStorage.setItem('music_wizard_briefs', JSON.stringify(this.wizardBriefs));
        },

        resetWizardForNewSong() {
            this.musicBrief = this.createEmptyMusicBrief();
            this.generationForm.genreId = null;
            this.generationForm.instrumental = true;
            this.generationForm.provider = '';
            this.generationForm.lyrics = '';
            this.generationForm.vocalMode = 'instrumental';
            this.generationForm.vocalLanguage = 'Tiếng Việt';
            this.generationForm.vocalGender = 'auto';
            this.generationForm.durationSeconds = 30;
            this.wizardEditingSongId = null;
            this.wizardEditingSongTitle = '';
            this.wizardAccessNote = '';
            this.wizardStep = 1;
            this.wizardFurthestStep = 1;
        },

        editSongWithWizard(song) {
            const saved = this.wizardBriefs[String(song.id)];
            const fallback = this.createEmptyMusicBrief();
            fallback.title = song.title || '';
            fallback.note = song.prompt || '';
            this.musicBrief = saved
                ? Object.assign(fallback, JSON.parse(JSON.stringify(saved)), {
                    instruments: Array.isArray(saved.instruments) ? saved.instruments : []
                })
                : fallback;
            this.generationForm.genreId = this.musicBrief.genreId;
            this.generationForm.instrumental = this.musicBrief.vocalMode === 'instrumental';
            this.wizardEditingSongId = song.id;
            this.wizardEditingSongTitle = song.title || 'bài nhạc này';
            this.wizardStep = 5;
            this.wizardFurthestStep = 5;
            window.scrollTo({ top: 0, behavior: 'smooth' });
            this.Toast.fire({
                icon: 'info',
                title: saved ? 'Đã nạp lại brief cũ. Hãy chọn bước cần chỉnh.' : 'Chưa có brief cũ, hãy bổ sung lựa chọn trước khi tạo phiên bản mới.'
            });
        },

        selectWizardGenre(genre) {
            this.musicBrief.genreId = genre.id;
            this.musicBrief.genreName = genre.name;
            this.generationForm.genreId = genre.id;
        },

        toggleWizardInstrument(instrument) {
            const instruments = this.musicBrief.instruments;
            if (instrument === 'Tự động') {
                this.musicBrief.instruments = instruments.includes('Tự động') ? [] : ['Tự động'];
                return;
            }
            this.musicBrief.instruments = instruments.filter(item => item !== 'Tự động');
            const index = this.musicBrief.instruments.indexOf(instrument);
            if (index >= 0) {
                this.musicBrief.instruments.splice(index, 1);
            } else {
                this.musicBrief.instruments.push(instrument);
            }
        },

        selectWizardVocalMode(mode) {
            if (!this.canSelectVocalMode(mode)) {
                this.wizardAccessNote = this.vocalModeAccessMessage(mode);
                return;
            }
            this.musicBrief.vocalMode = mode;
            this.generationForm.instrumental = mode === 'instrumental';
            if (mode === 'instrumental') {
                this.musicBrief.lyrics = '';
                this.musicBrief.vocalGender = 'auto';
            }
            this.syncWizardAccessNote();
        },

        selectWizardVocalGender(gender) {
            if (!this.canSelectVocalGender(gender)) {
                this.wizardAccessNote = this.providerUnsupportedMessage('gender');
                return;
            }
            this.musicBrief.vocalGender = gender;
            this.syncWizardAccessNote();
        },

        selectWizardVocalLanguage(language) {
            if (!this.canSelectVocalLanguage(language)) {
                this.wizardAccessNote = this.providerUnsupportedMessage('language');
                return;
            }
            this.musicBrief.vocalLanguage = language;
            this.syncWizardAccessNote();
        },

        selectWizardDuration(duration) {
            if (!this.isDurationTierAllowed(duration)) {
                this.wizardAccessNote = this.durationTierMessage();
                return;
            }
            this.musicBrief.duration = duration;
            this.syncWizardAccessNote();
        },

        isProviderTierAllowed(provider) {
            if (!provider) return false;
            const tierRank = { FREE: 0, CREATOR: 1, PRO: 2, STUDIO: 3 };
            return tierRank[this.effectiveUserTier] >= tierRank[this.providerRequiredTier(provider)];
        },

        providerDisplayState(provider) {
            if (!this.isProviderTierAllowed(provider)) return 'locked';
            return this.musicBrief.provider === provider.code ? 'selected' : 'available';
        },

        providerCardStyle(provider) {
            if (this.providerDisplayState(provider) !== 'locked') return { cursor: 'pointer' };
            return {
                opacity: '0.32',
                filter: 'grayscale(1)',
                pointerEvents: 'none',
                cursor: 'not-allowed',
                boxShadow: 'none',
                transform: 'none'
            };
        },

        optionDisplayState(isAllowed, isSelected) {
            if (!isAllowed) return 'locked';
            return isSelected ? 'selected' : 'available';
        },

        providerRequiredTier(provider) {
            return ({ audiocraft: 'FREE', musicapi: 'CREATOR', 'ace-step': 'PRO', suno: 'PRO' })[provider?.code] || 'PRO';
        },

        vocalModeRequiredTier(mode) {
            return mode === 'instrumental' ? 'FREE' : mode === 'ai-lyrics' ? 'CREATOR' : 'PRO';
        },

        isDurationTierAllowed(duration) {
            return this.durationToSeconds(duration) <= this.tierFeaturePolicy.maxDuration;
        },

        isVocalModeTierAllowed(mode) {
            if (mode === 'own-lyrics') return this.tierFeaturePolicy.ownLyrics;
            if (mode === 'ai-lyrics') return this.tierFeaturePolicy.aiLyrics;
            return true;
        },

        isVocalModeProviderSupported(mode) {
            return mode === 'instrumental' || this.selectedMusicProvider().supportsLyrics;
        },

        canSelectVocalMode(mode) {
            return this.isVocalModeTierAllowed(mode) && this.isVocalModeProviderSupported(mode);
        },

        canSelectVocalGender(gender) {
            return gender === 'auto' || this.selectedMusicProvider().supportsVocalGender;
        },

        canSelectVocalLanguage(language) {
            return language === 'Tiếng Việt' || this.selectedMusicProvider().supportsVocalLanguage;
        },

        vocalModeAccessMessage(mode) {
            if (!this.isVocalModeProviderSupported(mode)) return this.providerUnsupportedMessage('lyrics');
            return `Chức năng này yêu cầu gói ${this.vocalModeRequiredTier(mode)}.`;
        },

        providerTierMessage(provider) {
            return `${provider.name} chưa có trong gói ${this.userTierLabel}. Hãy chọn mô hình khác hoặc nâng cấp gói.`;
        },

        durationTierMessage() {
            const max = this.tierFeaturePolicy.maxDuration;
            const label = max >= 120 ? '2 phút' : `${max} giây`;
            return `Gói ${this.userTierLabel} hỗ trợ thời lượng tối đa ${label}.`;
        },

        vocalModeTierMessage(mode) {
            if (mode === 'own-lyrics') return 'Tự nhập lời nhạc dành cho gói PRO hoặc STUDIO.';
            return 'AI gợi ý lời chưa có trong gói FREE.';
        },

        syncWizardAccessNote() {
            this.wizardAccessNote = this.wizardTierRestrictionMessage || '';
        },

        providerUnsupportedMessage(feature) {
            const provider = this.selectedMusicProvider();
            if (feature === 'lyrics') {
                return `${provider.name} không hỗ trợ giọng hát và lời nhạc. Hãy kiểm tra lại mô hình AI.`;
            }
            if (feature === 'gender') {
                return `${provider.name} không hỗ trợ chọn giọng nam hoặc nữ.`;
            }
            if (feature === 'language') {
                return `${provider.name} không hỗ trợ chọn ngôn ngữ giọng hát.`;
            }
            return `${provider.name} không hỗ trợ chức năng này.`;
        },

        providerCapabilitySummary() {
            const provider = this.selectedMusicProvider();
            if (!provider.supportsLyrics) return `${provider.name}: chỉ hỗ trợ nhạc không lời.`;
            if (provider.supportsVocalGender) return `${provider.name}: hỗ trợ lời nhạc và chọn giọng hát.`;
            if (provider.supportsVocalLanguage) return `${provider.name}: hỗ trợ lời nhạc và chọn ngôn ngữ giọng hát.`;
            return `${provider.name}: hỗ trợ lời nhạc.`;
        },

        normalizeProviderVocalOptions(provider) {
            if (!provider.supportsLyrics && this.musicBrief.vocalMode !== 'instrumental') {
                this.selectWizardVocalMode('instrumental');
            }
            if (!provider.supportsVocalGender) this.musicBrief.vocalGender = 'auto';
            if (!provider.supportsVocalLanguage) this.musicBrief.vocalLanguage = 'Tiếng Việt';
        },

        onReferenceAudioSelected(event) {
            const file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
            const maxFileSize = 50 * 1000 * 1000;
            if (file && file.size > maxFileSize) {
                event.target.value = '';
                this.referenceAnalysis.file = null;
                this.Toast.fire({ icon: 'info', title: 'Bạn hãy chọn file nhạc không quá 50 MB nhé.' });
                return;
            }
            this.referenceAnalysis.file = file;
            this.referenceAnalysis.result = null;
        },

        analyzeReferenceAudio() {
            if (!this.canUseReferenceAnalysis) {
                this.wizardAccessNote = 'Phân tích nhạc tham khảo dành cho gói PRO hoặc STUDIO.';
                return;
            }
            if (!this.referenceAnalysis.configured) {
                this.Toast.fire({ icon: 'info', title: 'Máy phân tích chưa được cấu hình.' });
                return;
            }
            if (!this.currentUser) {
                Swal.fire({ icon: 'info', title: 'Cần đăng nhập', text: 'Hãy đăng nhập để lưu lịch sử phân tích nhạc tham khảo.', confirmButtonColor: '#16a34a' });
                return;
            }
            if (!this.referenceAnalysis.file) {
                this.Toast.fire({ icon: 'info', title: 'Hãy chọn file nhạc trước.' });
                return;
            }
            const data = new FormData();
            data.append('file', this.referenceAnalysis.file);
            this.referenceAnalysis.isLoading = true;
            axios.post('/api/music-analysis/reference', data)
                .then(response => {
                    this.referenceAnalysis.result = response.data;
                    this.loadReferenceAnalysisHistory();
                    const genre = response.data.genre;
                    if (genre && genre.id) this.selectWizardGenre(genre);
                    this.Toast.fire({ icon: 'success', title: response.data.cached ? 'Đã dùng lại kết quả đã phân tích.' : 'Đã nhận diện nhạc tham khảo.' });
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Không thể phân tích', text: error.response?.data?.message || 'Vui lòng kiểm tra máy phân tích thể loại và thử lại.', confirmButtonColor: '#dc3545' });
                })
                .finally(() => { this.referenceAnalysis.isLoading = false; });
        },

        loadReferenceAnalysisHistory() {
            if (!this.currentUser) return;
            axios.get('/api/music-analysis/history')
                .then(response => { this.referenceAnalysis.history = Array.isArray(response.data) ? response.data : []; })
                .catch(() => { this.referenceAnalysis.history = []; });
        },

        useReferenceAnalysis(result) {
            if (result && result.genre && result.genre.id) {
                this.selectWizardGenre(result.genre);
                this.Toast.fire({ icon: 'success', title: `Đã chọn thể loại ${result.genre.name}.` });
            } else {
                this.Toast.fire({ icon: 'info', title: 'Kết quả này chưa khớp thể loại hệ thống. Hãy chọn thủ công.' });
            }
        },

        selectMusicProvider(provider) {
            if (!this.isProviderTierAllowed(provider)) {
                this.wizardAccessNote = `${provider.name} yêu cầu gói ${this.providerRequiredTier(provider)}.`;
                return;
            }
            if (this.musicBrief.provider === provider.code) return;
            const wasVocalMode = this.musicBrief.vocalMode !== 'instrumental';
            this.musicBrief.provider = provider.code;
            this.generationForm.provider = provider.code;
            this.normalizeProviderVocalOptions(provider);
            this.syncWizardAccessNote();
            // Khả năng giọng hát/lời nhạc khác theo mô hình, nên cần chọn lại từ bước 4.
            this.wizardFurthestStep = Math.min(this.wizardFurthestStep, 3);
            if (!provider.supportsLyrics && wasVocalMode) {
                this.Toast.fire({ icon: 'info', title: this.providerUnsupportedMessage('lyrics') });
            }
        },

        selectedMusicProvider() {
            return this.musicProviders.find(item => item.code === this.musicBrief.provider) || this.musicProviders[0];
        },

        loadMusicProviderStatus() {
            axios.get('/api/songs/ai-status').then(response => {
                const statusByCode = response.data?.providers || {};
                this.musicProviders.forEach(provider => {
                    const capabilities = statusByCode[provider.code];
                    if (!capabilities) return;
                    provider.available = Boolean(capabilities.available);
                    provider.supportsLyrics = Boolean(capabilities.supportsLyrics);
                    provider.supportsVocalLanguage = Boolean(capabilities.supportsVocalLanguage);
                    provider.supportsVocalGender = Boolean(capabilities.supportsVocalGender);
                });
                if (this.musicBrief.provider && !this.isProviderTierAllowed(this.selectedMusicProvider())) {
                    this.musicBrief.provider = '';
                    this.generationForm.provider = '';
                }
                // Không tự chọn mô hình: người dùng phải chủ động chọn một mô hình được phép.
                if (this.musicBrief.provider) this.normalizeProviderVocalOptions(this.selectedMusicProvider());
            }).catch(() => {});
            axios.get('/api/music-analysis/status')
                .then(response => { this.referenceAnalysis.configured = Boolean(response.data?.configured); })
                .catch(() => { this.referenceAnalysis.configured = false; });
        },

        canMoveWizardForward() {
            if (this.wizardTierRestrictionMessage) {
                this.wizardAccessNote = this.wizardTierRestrictionMessage;
                return false;
            }
            if (this.wizardStep === 1 && !this.musicBrief.provider) {
                this.showWizardNote('Mời bạn chọn mô hình AI để bắt đầu nhé.');
                return false;
            }
            if (this.wizardStep === 1 && !this.musicBrief.audience) {
                this.showWizardNote('Mời bạn chọn nhóm nhu cầu phù hợp nhé.');
                return false;
            }
            if (this.wizardStep === 2) {
                const isCreatorReady = this.musicBrief.audience === 'creator' && this.musicBrief.useCase;
                const isCafeReady = this.musicBrief.audience === 'cafe' && this.musicBrief.venueStyle;
                if (!isCreatorReady && !isCafeReady) {
                    this.showWizardNote('Mời bạn chọn một bối cảnh sử dụng nhé.');
                    return false;
                }
            }
            return true;
        },

        nextWizardStep() {
            if (!this.canMoveWizardForward()) return;
            if (this.wizardStep < this.wizardSteps.length) {
                this.wizardStep++;
                this.wizardFurthestStep = Math.max(this.wizardFurthestStep, this.wizardStep);
            }
        },

        previousWizardStep() {
            if (this.wizardStep > 1) this.wizardStep--;
        },

        goToWizardStep(step) {
            if (step <= this.wizardFurthestStep) this.wizardStep = step;
        },

        showWizardNote(message) {
            this.Toast.fire({ title: message });
        },

        submitWizardMusic() {
            if (this.wizardTierRestrictionMessage) {
                this.wizardAccessNote = this.wizardTierRestrictionMessage;
                return;
            }
            const prompt = this.wizardGeneratedPrompt;
            if (!this.musicBrief.audience) {
                this.showWizardNote('Mời bạn hoàn thiện nhóm nhu cầu trước khi tạo nhạc nhé.');
                this.wizardStep = 1;
                return;
            }
            this.generationForm.prompt = prompt;
            this.generationForm.instrumental = this.musicBrief.vocalMode === 'instrumental';
            this.generationForm.genreId = this.musicBrief.genreId;
            this.generationForm.title = (this.musicBrief.title || '').trim();
            this.generationForm.provider = this.musicBrief.provider;
            this.generationForm.lyrics = this.musicBrief.vocalMode === 'own-lyrics' ? this.musicBrief.lyrics : '';
            this.generationForm.vocalMode = this.musicBrief.vocalMode;
            this.generationForm.vocalLanguage = this.musicBrief.vocalLanguage;
            this.generationForm.vocalGender = this.musicBrief.vocalGender;
            this.generationForm.durationSeconds = this.durationToSeconds(this.musicBrief.duration);

            if (this.wizardEditingSongId && this.currentUser) {
                this.createWizardVariation(prompt);
                return;
            }
            this.generateMusic();
        },

        createWizardVariation(prompt) {
            this.isGenerating = true;
            const originalId = this.wizardEditingSongId;
            const originalTitle = this.wizardEditingSongTitle;
            const title = this.generationForm.title || `${originalTitle} (Phiên bản mới)`;
            axios.post(`/api/songs/${originalId}/remix`, {
                prompt: prompt,
                title: title,
                instrumental: this.generationForm.instrumental
                , provider: this.generationForm.provider,
                lyrics: this.generationForm.lyrics,
                vocalMode: this.generationForm.vocalMode,
                vocalLanguage: this.generationForm.vocalLanguage,
                vocalGender: this.generationForm.vocalGender,
                durationSeconds: this.generationForm.durationSeconds
            }).then(response => {
                const data = response.data;
                this.registerQueuedSong(data, prompt, title);
                this.saveWizardBriefForSong(data.songId);
                this.Toast.fire({ icon: 'success', title: 'AI đang tạo phiên bản chỉnh sửa...' });
                this.resetWizardForNewSong();
            }).catch(error => {
                const errorMsg = error.response?.data?.message || 'Không thể tạo phiên bản mới. Vui lòng thử lại.';
                Swal.fire({ icon: 'error', title: 'Thất bại', text: errorMsg, confirmButtonColor: '#dc3545' });
            }).finally(() => {
                this.isGenerating = false;
            });
        },

        durationToSeconds(duration) {
            const value = String(duration || '30');
            if (value.includes('2 ph')) return 120;
            if (value.includes('60')) return 60;
            if (value.includes('15')) return 15;
            return 30;
        },

        createFromHome() {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Vui lòng đăng nhập để bắt đầu sáng tạo nhạc với AI.',
                    confirmButtonText: 'Đăng nhập ngay',
                    showCancelButton: true,
                    confirmButtonColor: '#16a34a',
                    cancelButtonColor: '#6e7881'
                }).then((result) => {
                    if (result.isConfirmed) {
                        window.location.href = '/login';
                    }
                });
                return;
            }
            if (!this.generationForm.prompt.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!', confirmButtonColor: '#16a34a' });
                return;
            }
            window.location.href = '/create?prompt=' + encodeURIComponent(this.generationForm.prompt) + '&auto=true';
        },

    }
};
