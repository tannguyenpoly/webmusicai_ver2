window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.navigation = {
    methods: {
        compactPagination(currentPage, totalPages) {
            const total = Number(totalPages) || 0;
            const current = Number(currentPage) || 0;
            if (total <= 7) return Array.from({ length: total }, (_, index) => index);

            const pages = [0];
            const start = Math.max(1, current - 1);
            const end = Math.min(total - 2, current + 1);
            if (start > 1) pages.push(-1);
            for (let page = start; page <= end; page += 1) pages.push(page);
            if (end < total - 2) pages.push(-2);
            pages.push(total - 1);
            return pages;
        },

        isTrackPlaying(songId) {
            return this.currentTrack && this.currentTrack.id === songId && this.isPlaying;
        },

        scrollRow(rowRef, direction) {
            const row = this.$refs[rowRef];
            if (row) {
                const scrollAmount = 600;
                row.scrollBy({
                    left: direction === 'left' ? -scrollAmount : scrollAmount,
                    behavior: 'smooth'
                });
            }
        },

        openExploreSection(section) {
            this.exploreReturnScrollY = window.scrollY || window.pageYOffset || 0;
            // Mỗi luồng quay lại dùng khóa riêng, tránh Collection ghi đè vị trí của Xem tất cả.
            sessionStorage.setItem('music_explore_section_scroll', String(this.exploreReturnScrollY));
            this.exploreSection = section;
            this.$nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
        },

        closeExploreSection() {
            this.exploreSection = '';
            const savedPosition = Number(sessionStorage.getItem('music_explore_section_scroll')) || this.exploreReturnScrollY || 0;
            this.$nextTick(() => {
                window.scrollTo({ top: savedPosition, behavior: 'auto' });
                sessionStorage.removeItem('music_explore_section_scroll');
            });
        },

        toggleFilterOption(option) {
            if (option === 'public') {
                this.workspaceFilters.public = !this.workspaceFilters.public;
                if (this.workspaceFilters.public) this.workspaceFilters.private = false;
            } else if (option === 'private') {
                this.workspaceFilters.private = !this.workspaceFilters.private;
                if (this.workspaceFilters.private) this.workspaceFilters.public = false;
            } else {
                this.workspaceFilters[option] = !this.workspaceFilters[option];
            }
        },

        resetWorkspaceFilters() {
            this.workspaceFilters.liked = false;
            this.workspaceFilters.public = false;
            this.workspaceFilters.private = false;
            this.workspaceFilters.pending = false;
        },

        formatAvatarUrl(url, name) {
            if (!url || typeof url !== 'string' || url.trim() === '' || url.includes('/images/default-avatar.png')) {
                return '/images/default-avatar.svg';
            }
            const normalized = url.trim();
            return normalized.startsWith('/') || normalized.startsWith('http://') || normalized.startsWith('https://') || normalized.startsWith('data:image/')
                ? normalized : '/images/default-avatar.svg';
        },

        onImageError(event, type = 'avatar') {
            const fallback = type === 'cover' ? '/images/default-cover.svg' : '/images/default-avatar.svg';
            if (event && event.target && event.target.dataset.fallbackApplied !== 'true') {
                event.target.dataset.fallbackApplied = 'true';
                event.target.src = fallback;
            }
        },

        getSongCover(song) {
            if (!song) return '/images/default-cover.svg';
            let id = typeof song === 'object' ? song.id : song;
            let customUrl = typeof song === 'object' ? song.coverUrl : null;

            if (!customUrl && id && this.publicSongs) {
                const found = this.publicSongs.find(s => s.id === id);
                if (found && found.coverUrl) {
                    customUrl = found.coverUrl;
                }
            }
            if (!customUrl && id && this.favoriteSongs) {
                const found = this.favoriteSongs.find(s => s.id === id);
                if (found && found.coverUrl) {
                    customUrl = found.coverUrl;
                }
            }
            if (!customUrl && id && this.profileGeneratedSongs) {
                const found = this.profileGeneratedSongs.find(s => s.id === id);
                if (found && found.coverUrl) {
                    customUrl = found.coverUrl;
                }
            }

            if (customUrl && customUrl.trim() !== '') {
                if (customUrl.startsWith('/images/')) {
                    let time = Date.now();
                    if (typeof song === 'object') {
                        const dateStr = song.created_at || song.createdAt;
                        if (dateStr) {
                            const parsed = Date.parse(dateStr);
                            if (!isNaN(parsed)) time = parsed;
                        }
                    }
                    return customUrl + '?v=' + time;
                }
                return customUrl;
            }

            const covers = [
                'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1515621061946-eff1c2a352bd?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1510915228340-29c85a43dcfe?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1516223725307-6f76b9ec8742?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1487058792275-0ad4aaf24ca7?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1484755560695-a4c73004ffd6?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1525201548942-d8c8709e4a88?w=400&auto=format&fit=crop&q=80'
            ];
            return covers[id % covers.length];
        },

        triggerSongCoverUpload(songId) {
            this.uploadingSongId = songId;
            this.$nextTick(() => {
                const elem = document.getElementById('songCoverFileInputHidden');
                if (elem) elem.click();
            });
        },

        uploadSongCoverFile(event) {
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
                title: 'Đang tải ảnh bìa lên...',
                text: 'Vui lòng chờ trong giây lát',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });

            const songId = this.uploadingSongId;
            axios.post(`/api/songs/${songId}/cover`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    const coverUrl = res.data.coverUrl;

                    const song = this.publicSongs.find(s => s.id === songId);
                    if (song) Vue.set(song, 'coverUrl', coverUrl);

                    if (this.currentTrack.id === songId) Vue.set(this.currentTrack, 'coverUrl', coverUrl);

                    const playlistSong = this.sessionPlaylist.find(s => s.id === songId);
                    if (playlistSong) Vue.set(playlistSong, 'coverUrl', coverUrl);

                    const favSong = this.favoriteSongs.find(s => s.id === songId);
                    if (favSong) Vue.set(favSong, 'coverUrl', coverUrl);

                    if (this.profileGeneratedSongs) {
                        const profSong = this.profileGeneratedSongs.find(s => s.id === songId);
                        if (profSong) Vue.set(profSong, 'coverUrl', coverUrl);
                    }
                    if (this.profileFavoriteSongs) {
                        const profFav = this.profileFavoriteSongs.find(s => s.id === songId);
                        if (profFav) Vue.set(profFav, 'coverUrl', coverUrl);
                    }

                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh bìa mới cho bài hát thành công!' });
                })
                .catch(err => {
                    let msg = 'Tải ảnh bìa thất bại!';
                    if (err.response && err.response.data && err.response.data.message) {
                        msg = err.response.data.message;
                    }
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
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

        goToSongDetail(songId) {
            const source = window.location.pathname + window.location.search;
            if (window.location.pathname === '/explore') {
                sessionStorage.setItem('music_explore_song_scroll', String(window.scrollY || window.pageYOffset || 0));
                sessionStorage.setItem('music_explore_return_pending', 'song');
            }
            window.location.href = `/song/${songId}?from=${encodeURIComponent(source)}`;
        },

        openCreatorProfile(username) {
            if (!username) return;
            if (window.location.pathname === '/explore') {
                sessionStorage.setItem('music_explore_profile_scroll', String(window.scrollY || window.pageYOffset || 0));
                sessionStorage.setItem('music_explore_return_pending', 'profile');
                window.location.href = `/profile?username=${encodeURIComponent(username)}&from=explore`;
                return;
            }
            window.location.href = `/profile?username=${encodeURIComponent(username)}`;
        },

        isProfileOpenedFromExplore() {
            return window.location.pathname === '/profile'
                && new URLSearchParams(window.location.search).get('from') === 'explore'
                && sessionStorage.getItem('music_explore_return_pending') === 'profile';
        },

        returnToExploreFromProfile() {
            if (this.isProfileOpenedFromExplore()) {
                window.location.href = '/explore?restore=profile';
                return;
            }
            window.history.back();
        },

        toggleHomeVideoSound(event) {
            const button = event?.currentTarget;
            const video = button?.closest('.home-video-card')?.querySelector('video');
            if (!button || !video) return;

            // Trình duyệt chỉ cho phép âm thanh sau thao tác trực tiếp của người dùng.
            video.volume = 0.3;
            video.muted = !video.muted;
            const enabled = !video.muted;
            const icon = button.querySelector('i');
            const label = button.querySelector('span');
            if (icon) icon.className = `ti ${enabled ? 'ti-volume' : 'ti-volume-off'}`;
            if (label) label.textContent = enabled ? 'Tắt âm thanh' : 'Bật âm thanh';
            button.setAttribute('aria-label', enabled ? 'Tắt âm thanh video' : 'Bật âm thanh video');
            button.setAttribute('title', enabled ? 'Tắt âm thanh video' : 'Bật âm thanh video');
            video.play().catch(() => {});
        },

        returnFromSongDetail() {
            const source = new URLSearchParams(window.location.search).get('from');
            const isSafeInternalPath = source && source.startsWith('/') && !source.startsWith('//') && !source.startsWith('/song/');
            if (isSafeInternalPath && source.startsWith('/explore')
                && sessionStorage.getItem('music_explore_return_pending') === 'song') {
                window.location.href = '/explore?restore=song';
                return;
            }
            window.location.href = isSafeInternalPath ? source : '/explore';
        },

        loadFollowStatus() {
            if (!this.profileUsername) return;
            axios.get(`/api/users/${this.profileUsername}/follow-status`)
                .then(res => {
                    this.isFollowing = res.data.isFollowing;
                    this.followersCount = res.data.followersCount;
                    this.followingCount = res.data.followingCount;
                })
                .catch(err => console.error(err));
        },

        toggleFollow() {
            if (!this.currentUser) {
                Swal.fire({ icon: 'warning', title: 'Đăng nhập', text: 'Vui lòng đăng nhập để thực hiện theo dõi!' });
                return;
            }
            const action = this.isFollowing ? 'unfollow' : 'follow';
            axios.post(`/api/users/${this.profileUsername}/${action}`)
                .then(res => {
                    this.isFollowing = !this.isFollowing;
                this.loadFollowStatus();
                this.loadProfilePublicCollections();
                    this.Toast.fire({ icon: 'success', title: res.data.message });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: err.response?.data?.message || 'Có lỗi xảy ra!' });
                });
        },

        openFollowersModal() {
            if (!this.profileUsername) return;
            this.followModalTitle = 'Người theo dõi';
            this.isLoadingFollowList = true;
            this.followList = [];
            this.showFollowModal = true;
            axios.get(`/api/users/${this.profileUsername}/followers`)
                .then(res => {
                    this.followList = res.data || [];
                })
                .catch(err => {
                    console.error("Lỗi tải danh sách người theo dõi:", err);
                    this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách người theo dõi.' });
                })
                .finally(() => {
                    this.isLoadingFollowList = false;
                });
        },

        openFollowingModal() {
            if (!this.profileUsername) return;
            this.followModalTitle = 'Đang theo dõi';
            this.isLoadingFollowList = true;
            this.followList = [];
            this.showFollowModal = true;
            axios.get(`/api/users/${this.profileUsername}/following`)
                .then(res => {
                    this.followList = res.data || [];
                })
                .catch(err => {
                    console.error("Lỗi tải danh sách đang theo dõi:", err);
                    this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách đang theo dõi.' });
                })
                .finally(() => {
                    this.isLoadingFollowList = false;
                });
        },

        closeFollowModal() {
            this.showFollowModal = false;
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

        getListensCount(song) {
            if (!song) return '0 lượt nghe';
            const realListens = typeof song === 'object' ? (song.listenCount || 0) : 0;
            return realListens + ' lượt nghe';
        },

        getLikesCount(song) {
            if (!song) return '0';
            return song.total_likes || 0;
        },

        toggleTheme() {
            this.isDarkMode = !this.isDarkMode;
            const currentTheme = this.isDarkMode ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', currentTheme);
            localStorage.setItem('music_theme', currentTheme);
        },

        loadUserTokenBalance(username) {
            axios.get(`/api/users/${username}/profile`)
                .then(response => {
                    if (response.data) {
                        if (response.data.token_balance !== undefined) this.userTokens = response.data.token_balance;
                        if (response.data.photo) this.userPhoto = response.data.photo;
                        this.userTier = response.data.accountTier || 'FREE';
                        this.userTierExpiresAt = response.data.tierExpiresAt || null;
                        this.authProvider = response.data.authProvider || 'LOCAL';
                        this.hasLocalPassword = response.data.hasLocalPassword !== false;
                    }
                })
                .catch(error => {
                    console.error("Lỗi tải thông tin số dư Credit:", error);
                });
        },

        loadPublicSongs() {
            axios.get('/api/songs/public')
                .then(response => { this.publicSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { console.error(error); });
        },

        loadCommunityCollections() {
            axios.all([axios.get('/api/playlists/public'), axios.get('/api/albums/public')])
                .then(axios.spread((playlistResponse, albumResponse) => {
                    this.communityPlaylists = Array.isArray(playlistResponse.data) ? playlistResponse.data : [];
                    this.communityAlbums = Array.isArray(albumResponse.data) ? albumResponse.data : [];
                }))
                .catch(error => console.error('Không thể tải bộ sưu tập cộng đồng:', error));
        },

        loadProfilePublicCollections() {
            if (!this.profileUsername) return;
            axios.all([axios.get('/api/playlists/public'), axios.get('/api/albums/public')])
                .then(axios.spread((playlistResponse, albumResponse) => {
                    this.profilePublicPlaylists = (playlistResponse.data || []).filter(item => item.username === this.profileUsername);
                    this.profilePublicAlbums = (albumResponse.data || []).filter(item => item.username === this.profileUsername);
                }))
                .catch(error => console.error('Không thể tải bộ sưu tập công khai của tác giả:', error));
        },

        openCollection(type, id) {
            if (window.location.pathname === '/explore') {
                sessionStorage.setItem('music_explore_collection_scroll', String(window.scrollY));
                sessionStorage.setItem('music_explore_return_pending', 'collection');
                window.location.href = `/${type === 'PLAYLIST' ? 'playlists' : 'albums'}/${id}?from=explore`;
                return;
            }
            window.location.href = `/${type === 'PLAYLIST' ? 'playlists' : 'albums'}/${id}`;
        },

        goBack() {
            if (new URLSearchParams(window.location.search).get('from') === 'explore') {
                const restoreType = sessionStorage.getItem('music_explore_return_pending') === 'collection'
                    ? 'collection'
                    : 'song';
                window.location.href = `/explore?restore=${restoreType}`;
                return;
            }
            window.history.back();
        },

        getCollectionCover(collection) {
            return collection && collection.coverUrl ? collection.coverUrl : '/images/default-cover.svg';
        },

        loadCollectionDetail(type, id) {
            this.isLoadingCollectionDetail = true;
            const endpoint = type === 'PLAYLIST' ? `/api/playlists/${id}` : `/api/albums/${id}`;
            axios.get(endpoint)
                .then(response => {
                    const data = response.data || {};
                    this.collectionDetail = { type, collection: data.playlist || data.album || null, songs: Array.isArray(data.songs) ? data.songs : [] };
                })
                .catch(error => {
                    Swal.fire('Không thể xem', error.response?.data?.message || 'Không thể mở bộ sưu tập này.', 'error');
                    this.collectionDetail = { type, collection: null, songs: [] };
                })
                .finally(() => { this.isLoadingCollectionDetail = false; });
        },

        loadGenres() {
            axios.get('/api/genres')
                .then(response => {
                    this.genres = Array.isArray(response.data) ? response.data : [];
                })
                .catch(() => {
                    this.genres = [];
                });
        },

    }
};
