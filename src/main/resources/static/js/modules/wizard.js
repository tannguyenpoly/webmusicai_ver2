window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.wizard = {
    methods: {
        migrateGuestSongs(guestId, username) {
            if (!guestId || !username) return;
            axios.post(`/api/auth/migrate?guestId=${guestId}&username=${username}`)
                .then(response => {
                    console.log("Đã chuyển quyền sở hữu nhạc:", response.data.message);
                    localStorage.removeItem('music_guest_id');
                })
                .catch(error => {
                    console.error("Lỗi chuyển quyền sở hữu nhạc:", error);
                });
        },
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
        },

        createEmptyMusicBrief() {
            return {
                audience: '', useCase: '', venueStyle: '', platform: '', timeOfDay: '',
                duration: '30 giây', genreId: null, genreName: '', mood: '', energy: 'Vừa phải',
                instruments: [], vocalMode: 'instrumental', vocalLanguage: 'Tiếng Việt',
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
            this.wizardEditingSongId = null;
            this.wizardEditingSongTitle = '';
            this.wizardStep = 1;
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
            this.musicBrief.vocalMode = mode;
            this.generationForm.instrumental = mode === 'instrumental';
            if (mode === 'instrumental') this.musicBrief.lyrics = '';
        },

        canMoveWizardForward() {
            if (this.wizardStep === 1 && !this.musicBrief.audience) {
                this.Toast.fire({ icon: 'info', title: 'Hãy chọn nhóm nhu cầu trước.' });
                return false;
            }
            if (this.wizardStep === 2) {
                const isCreatorReady = this.musicBrief.audience === 'creator' && this.musicBrief.useCase;
                const isCafeReady = this.musicBrief.audience === 'cafe' && this.musicBrief.venueStyle;
                if (!isCreatorReady && !isCafeReady) {
                    this.Toast.fire({ icon: 'info', title: 'Hãy chọn một bối cảnh sử dụng.' });
                    return false;
                }
            }
            return true;
        },

        nextWizardStep() {
            if (!this.canMoveWizardForward()) return;
            if (this.wizardStep < this.wizardSteps.length) this.wizardStep++;
        },

        previousWizardStep() {
            if (this.wizardStep > 1) this.wizardStep--;
        },

        goToWizardStep(step) {
            if (step <= this.wizardStep) this.wizardStep = step;
        },

        submitWizardMusic() {
            const prompt = this.wizardGeneratedPrompt;
            if (!this.musicBrief.audience) {
                this.Toast.fire({ icon: 'info', title: 'Hãy chọn nhu cầu tạo nhạc trước.' });
                this.wizardStep = 1;
                return;
            }
            this.generationForm.prompt = prompt;
            this.generationForm.instrumental = this.musicBrief.vocalMode === 'instrumental';
            this.generationForm.genreId = this.musicBrief.genreId;
            this.generationForm.title = (this.musicBrief.title || '').trim();

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
            }).then(response => {
                const data = response.data;
                this.registerQueuedSong(data, prompt, title);
                this.saveWizardBriefForSong(data.songId);
                this.Toast.fire({ icon: 'success', title: 'AI đang tạo phiên bản chỉnh sửa...' });
                this.resetWizardForNewSong();
            }).catch(error => {
                const errorMsg = error.response && error.response.data
                    ? (error.response.data.message || error.response.data)
                    : 'Không thể tạo phiên bản mới.';
                Swal.fire({ icon: 'error', title: 'Thất bại', text: errorMsg, confirmButtonColor: '#dc3545' });
            }).finally(() => {
                this.isGenerating = false;
            });
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
