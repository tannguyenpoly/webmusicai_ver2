// src/main/resources/static/js/modules/songManagement.js
export const songManagementModule = {
    data() {
        return {
            generationForm: {
                username: '',
                prompt: '',
                instrumental: true,
                genreId: null
            },
            isGenerating: false,
            currentTrack: { id: null, title: '', prompt: '', status: '', audioUrl: '' },
            pollingTimer: null,
            showQueue: false,
            uploadingSongId: null,
            isPlaying: false,
            isOnSongDetailPage: false,
            editingSongForm: { id: null, title: '', prompt: '', isPublic: false, coverUrl: '' },
            isSavingSongEdit: false,

            commentPagination: { content: [], number: 0, totalPages: 1, totalElements: 0 },
            isLoadingComments: false,
            isSubmittingComment: false,
            songTags: [],
            availableTags: [],
            selectedCreateTagIds: [],
            selectedDetailTagIds: [],
            newComment: { content: '' },
            newReply: { content: '' },
            replyingToCommentId: null,
            editingComment: null,
        };
    },
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
        isTrackPlaying(songId) {
            return this.currentTrack && this.currentTrack.id === songId && this.isPlaying;
        },
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
                    this.userTokens = data.remaining_tokens;
                    this.currentTrack = { id: data.songId, title: "AI đang tiến hành xử lý bài hát...", prompt: this.generationForm.prompt, status: "PENDING", audioUrl: "", username: this.currentUser || this.guestUsername };

                    if (window.location.pathname === '/' || window.location.pathname === '/profile' || window.location.pathname === '/create') {
                        this.profileGeneratedSongs.unshift({
                            id: data.songId,
                            title: "AI đang tiến hành xử lý bài hát...",
                            prompt: this.generationForm.prompt,
                            status: "PENDING",
                            audioUrl: "",
                            username: this.currentUser || this.guestUsername,
                            created_at: new Date().toISOString()
                        });
                    }
                    if (this.selectedCreateTagIds.length > 0) {
                        this.saveSongTags(data.songId, this.selectedCreateTagIds);
                    }
                    this.generationForm.prompt = '';
                    this.selectedCreateTagIds = [];
                    this.isGenerating = false;
                    this.startPollingStatus(data.songId);
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
                             if (typeof this.loadProfileGeneratedSongs === 'function' && (window.location.pathname === '/' || window.location.pathname === '/create' || (window.location.pathname === '/profile' && this.profileTab === 'generated'))) {
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
        renameSong(songId) {
            const track = this.publicSongs.find(s => s.id === songId) || this.currentTrack;
            if (!track) return;

            Swal.fire({
                title: 'Đổi tên bài hát',
                input: 'text',
                inputValue: track.title,
                inputPlaceholder: 'Nhập tên bài hát mới...',
                showCancelButton: true,
                confirmButtonText: 'Lưu',
                cancelButtonText: 'Hủy',
                confirmButtonColor: '#16a34a',
                inputValidator: (value) => {
                    if (!value || !value.trim()) {
                        return 'Tên bài hát không được để trống!';
                    }
                }
            }).then((result) => {
                if (result.isConfirmed) {
                    const newTitle = result.value.trim();
                    axios.put(`/api/songs/${songId}/setting`, { title: newTitle })
                        .then(res => {
                            if (this.currentTrack.id === songId) this.currentTrack.title = newTitle;

                            const publicSong = this.publicSongs.find(s => s.id === songId);
                            if (publicSong) publicSong.title = newTitle;

                            const favSong = this.favoriteSongs.find(s => s.id === songId);
                            if (favSong) favSong.title = newTitle;

                            const playSong = this.sessionPlaylist.find(s => s.id === songId);
                            if (playSong) playSong.title = newTitle;

                            if (this.profileGeneratedSongs) {
                                const profSong = this.profileGeneratedSongs.find(s => s.id === songId);
                                if (profSong) profSong.title = newTitle;
                            }
                            if (this.profileFavoriteSongs) {
                                const profFav = this.profileFavoriteSongs.find(s => s.id === songId);
                                if (profFav) profFav.title = newTitle;
                            }

                            this.Toast.fire({ icon: 'success', title: 'Đổi tên bài hát thành công!' });
                        })
                        .catch(err => {
                            Swal.fire('Lỗi', err.response?.data?.message || err.response?.data || 'Không thể đổi tên bài hát.', 'error');
                        });
                }
            });
        },
        openSongEditModal(song) {
            this.editingSongForm = {
                id: song.id,
                title: song.title,
                prompt: song.prompt,
                isPublic: song.isPublic !== undefined ? song.isPublic : song.is_public,
                coverUrl: song.coverUrl
            };
            this.isSavingSongEdit = false;

            const modalElem = document.getElementById('songEditModal');
            if (modalElem) {
                const modal = new bootstrap.Modal(modalElem);
                modal.show();
            }
        },
        triggerSongEditCoverUpload() {
            const fileInput = document.getElementById('songEditCoverFileInputHidden');
            if (fileInput) fileInput.click();
        },
        uploadSongEditCoverFile(event) {
            const file = event.target.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng chọn file hình ảnh!' });
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            Swal.fire({
                title: 'Đang tải ảnh lên...',
                text: 'Vui lòng chờ',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });
            axios.post(`/api/songs/${this.editingSongForm.id}/cover`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    this.editingSongForm.coverUrl = res.data.coverUrl;
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh bìa thành công!' });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Không thể tải ảnh lên!' });
                });
        },
        // This method is added to resolve "uploadSongCoverFile is not defined" error.
        // It assumes that when this method is called, `this.editingSongForm.id` is available
        // and it delegates to the existing uploadSongEditCoverFile.
        uploadSongCoverFile(event) {
            this.uploadSongEditCoverFile(event);
        },
        saveSongEdit() {
            this.isSavingSongEdit = true;
            axios.put(`/api/songs/${this.editingSongForm.id}/setting`, {
                title: this.editingSongForm.title,
                prompt: this.editingSongForm.prompt,
                is_public: this.editingSongForm.isPublic,
                cover_url: this.editingSongForm.coverUrl
            })
                .then(res => {
                    const songId = this.editingSongForm.id;
                    const newTitle = this.editingSongForm.title;
                    const newPrompt = this.editingSongForm.prompt;
                    const newIsPublic = this.editingSongForm.isPublic;
                    const newCoverUrl = this.editingSongForm.coverUrl;

                    const updateInList = (list) => {
                        if (!list) return;
                        const item = list.find(s => s.id === songId);
                        if (item) {
                            Vue.set(item, 'title', newTitle);
                            Vue.set(item, 'prompt', newPrompt);
                            Vue.set(item, 'isPublic', newIsPublic);
                            Vue.set(item, 'coverUrl', newCoverUrl);
                        }
                    };

                    updateInList(this.publicSongs);
                    updateInList(this.favoriteSongs);
                    updateInList(this.sessionPlaylist);
                    updateInList(this.profileGeneratedSongs);
                    updateInList(this.profileFavoriteSongs);

                    if (this.currentTrack.id === songId) {
                        Vue.set(this.currentTrack, 'title', newTitle);
                        Vue.set(this.currentTrack, 'prompt', newPrompt);
                        Vue.set(this.currentTrack, 'coverUrl', newCoverUrl);
                        Vue.set(this.currentTrack, 'isPublic', newIsPublic);
                    }

                    const modalElem = document.getElementById('songEditModal');
                    if (modalElem) {
                        const modal = bootstrap.Modal.getInstance(modalElem);
                        if (modal) modal.hide();
                    }

                    this.Toast.fire({ icon: 'success', title: 'Cập nhật bài viết thành công!' });
                    this.loadPublicSongs();
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || err.response?.data || 'Không thể cập nhật bài viết!' });
                })
                .finally(() => {
                    this.isSavingSongEdit = false;
                });
        },
        cancelMusicGeneration(song) {
            const target = song || this.currentTrack;
            if (!target || !target.id || target.status !== 'PENDING') return;
            Swal.fire({
                icon: 'question',
                title: 'Dừng tạo nhạc?',
                text: 'Tác vụ sẽ được đánh dấu đã hủy và 1 token được hoàn lại.',
                showCancelButton: true,
                confirmButtonText: 'Dừng và hoàn token',
                cancelButtonText: 'Tiếp tục chờ',
                confirmButtonColor: '#dc3545'
            }).then(result => {
                if (!result.isConfirmed) return;
                axios.post(`/api/songs/${target.id}/cancel`)
                    .then(response => {
                        target.status = 'CANCELLED';
                        this.userTokens = response.data.remaining_tokens;
                        this.isGenerating = false;
                        if (this.currentTrack.id === target.id) {
                            this.currentTrack.status = 'CANCELLED';
                            this.currentTrack.title = 'Đã dừng tạo nhạc';
                            this.currentTrack.audioUrl = '';
                        }
                        const profileSong = this.profileGeneratedSongs.find(item => item.id === target.id);
                        if (profileSong) profileSong.status = 'CANCELLED';
                        if (this.pollingTimer) {
                            clearInterval(this.pollingTimer);
                            this.pollingTimer = null;
                        }
                        this.Toast.fire({ icon: 'success', title: 'Đã dừng và hoàn lại 1 token' });
                    })
                    .catch(error => {
                        const message = error.response?.data?.message || 'Không thể dừng tác vụ';
                        Swal.fire('Không thể dừng', message, 'error');
                    });
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
        loadComments(songId, loadMore = false) {
            if (!songId) return;
            this.isLoadingComments = true;
            const pageToLoad = loadMore ? this.commentPagination.number + 1 : 0;
            axios.get(`/api/songs/${songId}/comments?page=${pageToLoad}&size=10`)
                .then(response => {
                    if (loadMore) { response.data.content = this.commentPagination.content.concat(response.data.content); }
                    this.commentPagination = response.data;
                })
                .catch(error => { this.Toast.fire({ icon: 'error', title: 'Không thể tải bình luận.' }); })
                .finally(() => { this.isLoadingComments = false; });
        },
        postComment(songId, parentId = null) {
            if (this.isSubmittingComment) return;
            const isReply = parentId !== null;
            const content = isReply ? this.newReply.content.trim() : this.newComment.content.trim();
            if (!content) { this.Toast.fire({ icon: 'warning', title: 'Vui lòng nhập nội dung.' }); return; }

            this.isSubmittingComment = true;
            const payload = { content: content, parent_id: parentId };
            axios.post(`/api/songs/${songId}/comments`, payload)
                .then(response => {
                    if (parentId) {
                        const parentComment = this.commentPagination.content.find(c => c.id === parentId);
                        if (parentComment) {
                            if (!parentComment.replies) { parentComment.replies = []; }
                            parentComment.replies.push(response.data);
                        }
                        this.newReply.content = '';
                    } else {
                        if (!response.data.replies) { response.data.replies = []; }
                        this.commentPagination.content.unshift(response.data);
                        this.commentPagination.totalElements++;
                        this.newComment.content = '';
                    }
                    this.replyingToCommentId = null;
                    this.Toast.fire({ icon: 'success', title: 'Đã gửi bình luận!' });
                })
                .catch(error => {
                    const msg = (error.response && error.response.data && error.response.data.message) ? error.response.data.message : 'Không thể gửi bình luận.';
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                })
                .finally(() => { this.isSubmittingComment = false; });
        },
        toggleReplyForm(commentId) {
            this.replyingToCommentId = (this.replyingToCommentId === commentId) ? null : commentId;
            this.newReply.content = '';
        },
        editComment(comment) {
            this.editingComment = { id: comment.id, content: comment.content };
        },
        cancelEditComment() {
            this.editingComment = null;
        },
        saveComment(originalComment) {
            if (this.isSubmittingComment) return;
            if (!this.editingComment || !this.editingComment.content.trim()) {
                this.Toast.fire({ icon: 'warning', title: 'Nội dung không được để trống.' });
                return;
            }
            this.isSubmittingComment = true;
            axios.put(`/api/songs/comments/${this.editingComment.id}`, { content: this.editingComment.content })
                .then(response => {
                    originalComment.content = response.data.content;
                    this.editingComment = null;
                    this.Toast.fire({ icon: 'success', title: 'Đã cập nhật bình luận!' });
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể cập nhật bình luận.' });
                })
                .finally(() => { this.isSubmittingComment = false; });
        },
        deleteComment(commentId, index, parentIndex) {
            Swal.fire({ title: 'Xác nhận xóa?', text: "Bình luận này sẽ bị xóa vĩnh viễn!", icon: 'warning', showCancelButton: true, confirmButtonColor: '#dc3545', cancelButtonColor: '#6e7881', confirmButtonText: 'Xóa', cancelButtonText: 'Hủy' })
                .then((result) => {
                    if (result.isConfirmed) {
                        axios.delete(`/api/songs/comments/${commentId}`)
                            .then(() => {
                                if (parentIndex !== null) { this.commentPagination.content[parentIndex].replies.splice(index, 1); }
                                else { this.commentPagination.content.splice(index, 1); this.commentPagination.totalElements--; }
                                this.Toast.fire({ icon: 'success', title: 'Đã xóa bình luận.' });
                            })
                            .catch(error => Swal.fire('Lỗi!', 'Không thể xóa bình luận.', 'error'));
                    }
                });
        },
    }
};
