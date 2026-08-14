window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.library = {
    methods: {
        loadFavoriteSongs() {
            if (!this.currentUser) { window.location.href = '/login'; return; }
            this.isLoadingFavorites = true;
            axios.get('/api/songs/my-favorites')
                .then(response => { this.favoriteSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách yêu thích.' }); })
                .finally(() => { this.isLoadingFavorites = false; });
        },

        triggerAvatarUpload() {
            if (this.$refs.avatarFileInput) this.$refs.avatarFileInput.click();
            else {
                const elem = document.getElementById('avatarFileInputHidden');
                if (elem) elem.click();
            }
        },

        uploadAvatarFile(event) {
            const file = event.target.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng chọn file hình ảnh (.jpg, .png, .webp, .gif)!' });
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Dung lượng ảnh tối đa là 5MB!' });
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            Swal.fire({
                title: 'Đang tải ảnh lên...',
                text: 'Vui lòng chờ trong giây lát',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });
            axios.post(`/api/users/${this.currentUser}/avatar`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    const newPhoto = res.data.photo;
                    this.userPhoto = newPhoto;
                    if (this.profilePageData) this.profilePageData.photo = newPhoto;
                    if (this.profileForm) this.profileForm.photo = newPhoto;
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh đại diện mới thành công!' });
                })
                .catch(err => {
                    let msg = 'Tải ảnh đại diện thất bại!';
                    if (err.response && err.response.data && err.response.data.message) msg = err.response.data.message;
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                });
        },

        loadProfilePageData() {
            axios.get(`/api/users/${this.profileUsername}/profile`)
                .then(res => {
                    this.profilePageData = res.data;
                    if (res.data.total_songs !== undefined) {
                        this.profileStats.total = res.data.total_songs;
                        this.profileStats.completed = res.data.completed_songs;
                        this.profileStats.pending = res.data.pending_songs;
                    }
                    if (res.data.total_favorites !== undefined) this.profileStats.totalFavorites = res.data.total_favorites;
                })
                .catch(err => console.error(err));
        },

        loadProfileGeneratedSongs(loadMore = false) {
            if (!loadMore) {
                this.profileSongPagination.page = 0;
                this.profileGeneratedSongs = [];
            }
            this.isLoadingProfileSongs = true;
            axios.get(`/api/users/${this.profileUsername}/songs?page=${this.profileSongPagination.page}&size=${this.profileSongPagination.size}`)
                .then(res => {
                    const data = res.data;
                    const content = data.content ? data.content : Array.isArray(data) ? data : [];
                    if (loadMore) this.profileGeneratedSongs = this.profileGeneratedSongs.concat(content);
                    else this.profileGeneratedSongs = content;

                    if (data.content) {
                        this.profileSongPagination.hasMore = !data.last;
                        if (this.profileStats.total === 0 || !loadMore) this.profileStats.total = data.totalElements;
                    } else {
                        this.profileSongPagination.hasMore = false;
                        this.profileStats.total = this.profileGeneratedSongs.length;
                    }
                })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileSongs = false; });
        },

        loadMoreProfileSongs() {
            this.profileSongPagination.page++;
            this.loadProfileGeneratedSongs(true);
        },

        switchToFavTab() {
            this.profileTab = 'favorites';
            if (this.profileFavoriteSongs.length === 0) this.loadProfileFavorites();
        },

        loadProfileFavorites() {
            this.isLoadingProfileFav = true;
            axios.get('/api/songs/my-favorites')
                .then(res => {
                    this.profileFavoriteSongs = Array.isArray(res.data) ? res.data : [];
                    this.profileStats.totalFavorites = this.profileFavoriteSongs.length;
                })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileFav = false; });
        },

        loadLibraryAlbums() {
            if (!this.currentUser) return;
            this.isLoadingLibraryAlbums = true;
            axios.get(`/api/albums/user/${encodeURIComponent(this.currentUser)}?page=0&size=50`)
                .then(res => {
                    const data = res.data;
                    this.libraryAlbums = data && Array.isArray(data.content)
                        ? data.content
                        : Array.isArray(data) ? data : [];
                })
                .catch(err => {
                    console.error('Không thể tải album:', err);
                    this.libraryAlbums = [];
                })
                .finally(() => { this.isLoadingLibraryAlbums = false; });
        },

        openAlbumModal() {
            this.newAlbumForm = { title: '', description: '', isPublic: false };
            const modalElement = document.getElementById('albumManagerModal');
            if (modalElement && window.bootstrap) window.bootstrap.Modal.getOrCreateInstance(modalElement).show();
        },

        createLibraryAlbum() {
            const title = (this.newAlbumForm.title || '').trim();
            if (!title) {
                this.Toast.fire({ icon: 'warning', title: 'Hãy nhập tên album' });
                return;
            }
            this.isCreatingLibraryAlbum = true;
            axios.post('/api/albums', {
                title,
                description: (this.newAlbumForm.description || '').trim(),
                isPublic: !!this.newAlbumForm.isPublic
            }).then(res => {
                this.libraryAlbums.unshift(res.data);
                const modalElement = document.getElementById('albumManagerModal');
                if (modalElement && window.bootstrap) window.bootstrap.Modal.getInstance(modalElement)?.hide();
                this.Toast.fire({ icon: 'success', title: 'Đã tạo album' });
            }).catch(err => {
                Swal.fire('Lỗi', err.response?.data?.message || 'Không thể tạo album', 'error');
            }).finally(() => { this.isCreatingLibraryAlbum = false; });
        },

        deleteLibraryAlbum(album) {
            Swal.fire({ title: `Xóa album "${album.title}"?`, text: 'Các bài trong album không bị xóa.', icon: 'warning', showCancelButton: true,
                confirmButtonText: 'Xóa album', cancelButtonText: 'Hủy', confirmButtonColor: '#dc3545' }).then(result => {
                if (!result.isConfirmed) return;
                axios.delete(`/api/albums/${album.id}`).then(() => {
                    this.libraryAlbums = this.libraryAlbums.filter(item => item.id !== album.id);
                    this.Toast.fire({ icon: 'success', title: 'Đã xóa album' });
                }).catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể xóa album', 'error'));
            });
        },

        toggleLibraryAlbumPrivacy(album) {
            axios.put(`/api/albums/${album.id}`, { isPublic: !album.isPublic })
                .then(response => {
                    album.isPublic = response.data.isPublic;
                    this.Toast.fire({ icon: 'success', title: album.isPublic ? 'Album đã công khai' : 'Album đã chuyển riêng tư' });
                    this.loadCommunityCollections();
                    this.loadProfilePublicCollections();
                })
                .catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể đổi quyền xem album', 'error'));
        },

        addSongToLibraryAlbum(song) {
            if (this.libraryAlbums.length === 0) {
                Swal.fire({ icon: 'info', title: 'Chưa có album', text: 'Hãy tạo album trước khi thêm bài nhạc.' });
                return;
            }
            const choices = this.libraryAlbums.reduce((all, album) => { all[album.id] = album.title; return all; }, {});
            Swal.fire({ title: 'Thêm vào album', input: 'select', inputOptions: choices, inputPlaceholder: 'Chọn album', showCancelButton: true,
                confirmButtonText: 'Thêm bài', cancelButtonText: 'Hủy', confirmButtonColor: '#16a34a', inputValidator: value => !value && 'Hãy chọn album' }).then(result => {
                if (!result.isConfirmed) return;
                axios.post(`/api/albums/${result.value}/songs/${song.id}`).then(() => {
                    this.Toast.fire({ icon: 'success', title: 'Đã thêm bài vào album' });
                }).catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể thêm bài vào album', 'error'));
            });
        },

        toggleProfileSongVisibility(song) {
            axios.put(`/api/songs/${song.id}/visibility`)
                .then(res => {
                    song.isPublic = res.data.isPublic !== undefined ? res.data.isPublic : !song.isPublic;
                    this.Toast.fire({ icon: 'success', title: song.isPublic ? 'Đã công khai bài hát' : 'Đã chuyển thành riêng tư' });
                })
                .catch(err => {
                    Swal.fire('Lỗi', 'Không thể đổi trạng thái bài hát', 'error');
                });
        },

        deleteGeneratedSong(song) {
            Swal.fire({
                title: 'Xác nhận xóa?',
                text: "Bài nhạc này sẽ bị xóa vĩnh viễn khỏi hệ thống!",
                icon: 'warning',
                showCancelButton: true,
                confirmButtonColor: '#dc3545',
                cancelButtonText: 'Hủy',
                confirmButtonText: 'Xóa ngay'
            }).then(result => {
                if (result.isConfirmed) {
                    axios.delete(`/api/songs/${song.id}`)
                        .then(() => {
                            this.profileGeneratedSongs = this.profileGeneratedSongs.filter(s => s.id !== song.id);
                            this.profileStats.total--;
                            if (song.status === 'COMPLETED') this.profileStats.completed--;
                            if (song.status === 'PENDING') this.profileStats.pending--;
                            this.Toast.fire({ icon: 'success', title: 'Đã xóa bài nhạc thành công.' });
                        })
                        .catch(err => Swal.fire('Lỗi', 'Không thể xóa bài nhạc.', 'error'));
                }
            });
        },

        removeFavAndUpdate(song) {
            axios.post(`/api/songs/${song.id}/like`)
                .then(res => {
                    this.profileFavoriteSongs = this.profileFavoriteSongs.filter(s => s.id !== song.id);
                    if (this.profileStats.totalFavorites > 0) this.profileStats.totalFavorites--;
                    this.Toast.fire({ icon: 'success', title: 'Đã bỏ yêu thích bài hát.' });
                })
                .catch(err => this.Toast.fire({ icon: 'error', title: 'Lỗi xử lý.' }));
        },

        registerQueuedSong(data, prompt, title) {
            const displayTitle = title || 'AI đang tiến hành xử lý bài hát...';
            this.userTokens = data.remaining_tokens;
            this.currentTrack = {
                id: data.songId,
                title: displayTitle,
                prompt: prompt,
                status: 'PENDING',
                audioUrl: '',
                username: this.currentUser
            };

            if (window.location.pathname === '/' || window.location.pathname === '/profile' || window.location.pathname === '/create') {
                this.profileGeneratedSongs.unshift({
                    id: data.songId,
                    title: displayTitle,
                    prompt: prompt,
                    status: 'PENDING',
                    audioUrl: '',
                    username: this.currentUser,
                    created_at: new Date().toISOString()
                });
            }
            this.startPollingStatus(data.songId);
        },

    }
};
