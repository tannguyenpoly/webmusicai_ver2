window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.music = {
    methods: {
        generateMusic() {
            if (!this.generationForm.prompt.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!', confirmButtonColor: '#16a34a' });
                return;
            }
            this.isGenerating = true;
            this.generationForm.username = this.currentUser || this.guestUsername;
            axios.post('/api/songs/generate', this.generationForm)
                .then(response => {
                    const data = response.data;
                    this.Toast.fire({ icon: 'success', title: 'AI đang xử lý giai điệu ngầm...' });
                    const prompt = this.generationForm.prompt;
                    const title = this.generationForm.title;
                    this.registerQueuedSong(data, prompt, title);
                    if (window.location.pathname === '/create' && this.musicBrief.audience) {
                        this.saveWizardBriefForSong(data.songId);
                    }
                    if (this.selectedCreateTagIds.length > 0) {
                        this.saveSongTags(data.songId, this.selectedCreateTagIds);
                    }
                    this.generationForm.prompt = '';
                    this.generationForm.title = '';
                    this.selectedCreateTagIds = [];
                    this.isGenerating = false;
                })
                .catch(error => {
                    this.isGenerating = false;
                    const errorMsg = error.response && error.response.data ? (error.response.data.message || error.response.data) : 'Lỗi kết nối lõi AI.';
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: errorMsg, confirmButtonColor: '#dc3545' });
                });
        },

        startPollingStatus(songId) {
            if (this.pollingTimer) clearInterval(this.pollingTimer);
            this.pollingTimer = setInterval(() => {
                axios.get(`/api/songs/${songId}/status`)
                    .then(response => {
                        const statusData = response.data;
                        if (this.currentTrack.id === songId) this.currentTrack.status = statusData.status;
                        if (statusData.status === 'COMPLETED') {
                            clearInterval(this.pollingTimer);
                            this.currentTrack.title = statusData.title;
                            this.currentTrack.audioUrl = statusData.audioUrl;
                            this.loadPublicSongs();
                             if (window.location.pathname === '/' || window.location.pathname === '/create' || (window.location.pathname === '/profile' && this.profileTab === 'generated')) {
                                this.loadProfileGeneratedSongs();
                            }
                            this.Toast.fire({ icon: 'success', title: `Sinh xong bài: ${statusData.title}!` });
                            this.$nextTick(() => { const audio = document.getElementById('audio-element'); if (audio) { audio.load(); audio.play(); } });
                        } else if (statusData.status === 'FAILED') {
                            clearInterval(this.pollingTimer);
                            Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Quá trình tạo nhạc thất bại!' });
                        } else if (statusData.status === 'CANCELLED') {
                            clearInterval(this.pollingTimer);
                            this.isGenerating = false;
                            this.Toast.fire({ icon: 'info', title: 'Đã dừng tạo nhạc và hoàn token' });
                        }
                    })
                    .catch(() => { clearInterval(this.pollingTimer); });
            }, 3000);
        },

        playTrack(song) {
            if (this.currentTrack.id === song.id && this.currentTrack.status === 'COMPLETED') {
                const audio = document.getElementById('audio-element');
                if (audio) {
                    if (audio.paused) {
                        audio.play().then(() => { this.isPlaying = true; }).catch(err => console.error(err));
                    } else {
                        audio.pause();
                        this.isPlaying = false;
                    }
                    return;
                }
            }
            if (this.pollingTimer && this.currentTrack.id === song.id && this.currentTrack.status === 'PENDING') return;
            if (this.pollingTimer) clearInterval(this.pollingTimer);

            this.currentTrack = {
                id: song.id,
                title: song.title,
                prompt: song.prompt,
                status: 'COMPLETED',
                audioUrl: song.audioUrl,
                coverUrl: song.coverUrl,
                username: song.username,
                listenCount: song.listenCount || 0
            };
            this.isPlaying = true;
            this.incrementListenCount(song);
            this.$nextTick(() => {
                const audio = document.getElementById('audio-element');
                if (audio) {
                    audio.load();
                    audio.play().then(() => { this.isPlaying = true; }).catch(err => console.error(err));
                }
            });
        },

        incrementListenCount(song) {
            if (!song || !song.id) return;
            axios.post(`/api/songs/${song.id}/play`)
                .then(response => {
                    if (response.data && response.data.success) {
                        song.listenCount = response.data.listenCount;
                        if (this.currentTrack.id === song.id) this.currentTrack.listenCount = response.data.listenCount;
                    }
                })
                .catch(err => console.error("Lỗi tăng lượt nghe:", err));
        },

        loadSessionPlaylist() {
            const data = sessionStorage.getItem('music_session_playlist');
            this.sessionPlaylist = data ? JSON.parse(data) : [];
        },
        addToPlaylist(song) {
            const isExist = this.sessionPlaylist.some(item => item.id === song.id);
            if (!isExist) {
                this.sessionPlaylist.push(song);
                sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
                this.Toast.fire({ icon: 'success', title: 'Đã thêm vào danh sách phát tạm' });
            } else {
                this.Toast.fire({ icon: 'info', title: 'Bài hát đã có trong danh sách phát' });
            }
        },
        removeTrack(index) {
            this.sessionPlaylist.splice(index, 1);
            sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
            this.Toast.fire({ icon: 'warning', title: 'Đã xóa bài hát khỏi danh sách phát' });
        },
        clearPlaylist() {
            this.sessionPlaylist = [];
            sessionStorage.removeItem('music_session_playlist');
            this.Toast.fire({ icon: 'info', title: 'Đã xóa danh sách chờ phát' });
        },

        loadSingleSongAndComments(songId) {
            axios.get(`/api/songs/${songId}/status`)
                .then(response => {
                    this.currentTrack = response.data;
                    this.profileUsername = response.data.username;
                    this.loadFollowStatus();
                    this.loadComments(songId);
                    this.loadSongTags(songId);
                })
                .catch(error => {
                    console.error("Không thể tải thông tin bài hát:", error);
                    Swal.fire('Lỗi', 'Không tìm thấy bài hát hoặc bạn không có quyền truy cập.', 'error');
                });
        },

        loadAvailableTags() {
            axios.get('/api/tags')
                .then(response => { this.availableTags = response.data || []; })
                .catch(error => console.error('Unable to load tags:', error));
        },

        loadSongTags(songId) {
            axios.get(`/api/songs/${songId}/tags`)
                .then(response => {
                    this.songTags = response.data || [];
                    this.selectedDetailTagIds = this.songTags.map(tag => tag.tagId);
                })
                .catch(error => console.error('Unable to load song tags:', error));
        },

        toggleCreateTag(tagId) {
            const index = this.selectedCreateTagIds.indexOf(tagId);
            if (index >= 0) this.selectedCreateTagIds.splice(index, 1);
            else this.selectedCreateTagIds.push(tagId);
        },

        toggleDetailTag(tagId) {
            const index = this.selectedDetailTagIds.indexOf(tagId);
            if (index >= 0) this.selectedDetailTagIds.splice(index, 1);
            else this.selectedDetailTagIds.push(tagId);
        },

        saveSongTags(songId, tagIds) {
            return axios.post(`/api/songs/${songId}/tags`, { tagIds: tagIds })
                .then(() => {
                    if (this.currentTrack && this.currentTrack.id === songId) this.loadSongTags(songId);
                });
        },

        saveDetailTags() {
            this.saveSongTags(this.currentTrack.id, this.selectedDetailTagIds)
                .then(() => this.Toast.fire({ icon: 'success', title: 'Đã cập nhật tag' }))
                .catch(() => this.Toast.fire({ icon: 'error', title: 'Không thể cập nhật tag' }));
        },

        toggleLike(song) {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Bạn cần đăng nhập để "thả tim" cho bài hát này.',
                    confirmButtonText: 'Đăng nhập ngay',
                    showCancelButton: true,
                    cancelButtonColor: '#6e7881',
                    confirmButtonColor: '#16a34a',
                    cancelButtonText: 'Hủy'
                }).then((result) => {
                    if (result.isConfirmed) window.location.href = '/login';
                });
                return;
            }
            const originalLikedState = song.liked_by_me;
            const originalLikeCount = song.total_likes;
            song.liked_by_me = !song.liked_by_me;
            song.total_likes += song.liked_by_me ? 1 : -1;
            if (window.location.pathname.startsWith('/favorites') && !song.liked_by_me) {
                const index = this.favoriteSongs.findIndex(s => s.id === song.id);
                if (index > -1) this.favoriteSongs.splice(index, 1);
            }
            axios.post(`/api/songs/${song.id}/like`)
                .then(response => {
                    song.liked_by_me = response.data.liked;
                    song.total_likes = response.data.total_likes;
                    this.Toast.fire({ icon: 'success', title: response.data.message });
                })
                .catch(error => {
                    song.liked_by_me = originalLikedState;
                    song.total_likes = originalLikeCount;
                    if (window.location.pathname.startsWith('/favorites') && song.liked_by_me) {
                        const isExist = this.favoriteSongs.some(s => s.id === song.id);
                        if (!isExist) this.favoriteSongs.push(song);
                    }
                    this.Toast.fire({ icon: 'error', title: error.response?.data?.message || 'Đã có lỗi xảy ra' });
                });
        },

    }
};
