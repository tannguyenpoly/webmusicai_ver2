new Vue({
    el: '#app',
    data: {
        isDarkMode: localStorage.getItem('music_theme') !== 'light',

        currentUser: null,
        isAdmin: false,
        userTokens: 0,
        publicSongs: [],
        sessionPlaylist: [],
        favoriteSongs: [],
        isLoadingFavorites: false,
        packages: [],
        myOrders: [],
        isLoadingPackages: false,
        isLoadingOrders: false,

        generationForm: {
            username: '',
            prompt: '',
            instrumental: true
        },
        isGenerating: false,
        currentTrack: { id: null, title: '', prompt: '', status: '', audioUrl: '' },

        loginForm: { username: '', password: '' },
        registerForm: { username: '', fullname: '', email: '', password: '', confirmPassword: '' },
        filters: { keyword: '' },
        pollingTimer: null,

        profileModalTab: 'info',
        showProfileModal: false,
        profileForm: { fullname: '', email: '', photo: '' },
        changePasswordForm: { oldPassword: '', newPassword: '', confirmNewPassword: '' },

        commentPagination: { content: [], number: 0, totalPages: 1, totalElements: 0 },
        isLoadingComments: false,
        newComment: { content: '' },
        replyingToCommentId: null,
        editingComment: null,

        profilePageData: {},
        profileStats: { total: 0, completed: 0, pending: 0 },
        profileTab: 'generated',
        profileGeneratedSongs: [],
        profileFavoriteSongs: [],
        isLoadingProfileSongs: false,
        isLoadingProfileFav: false,
        profileSongPagination: { page: 0, size: 10, hasMore: false }
    },
    computed: {
        filteredSongs() {
            let result = [...this.publicSongs];
            if (this.filters.keyword && this.filters.keyword.trim() !== '') {
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw))
                );
            }
            return result;
        }
    },
    created() {
        axios.interceptors.request.use(config => {
            const token = localStorage.getItem('jwt_token');
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
            return config;
        }, error => {
            return Promise.reject(error);
        });
    },
    mounted() {
        this.Toast = Swal.mixin({
            toast: true,
            position: 'top-end',
            showConfirmButton: false,
            timer: 3000,
            timerProgressBar: true,
            didOpen: (toast) => {
                toast.addEventListener('mouseenter', Swal.stopTimer)
                toast.addEventListener('mouseleave', Swal.resumeTimer)
            }
        });

        const urlParams = new URLSearchParams(window.location.search);
        const paymentStatus = urlParams.get('status');
        if (paymentStatus) {
            if (paymentStatus === 'success') {
                Swal.fire({ icon: 'success', title: 'Thanh toán thành công!', text: 'Hệ thống đã cập nhật token vào tài khoản của bạn.', confirmButtonColor: '#16a34a' });
            } else if (paymentStatus === 'failed') {
                Swal.fire({ icon: 'error', title: 'Thanh toán thất bại!', text: 'Giao dịch chưa hoàn tất hoặc đã bị hủy.', confirmButtonColor: '#dc3545' });
            } else if (paymentStatus === 'invalid') {
                Swal.fire({ icon: 'warning', title: 'Cảnh báo', text: 'Giao dịch không hợp lệ hoặc dữ liệu bị sai lệch.', confirmButtonColor: '#ffc107' });
            }
            window.history.replaceState(null, null, window.location.pathname);
        }

        const savedUser = localStorage.getItem('music_username');
        const savedToken = localStorage.getItem('jwt_token');

        if (savedUser && savedToken) {
            this.currentUser = savedUser;
            this.isAdmin = localStorage.getItem('music_is_admin') === 'true';
            this.generationForm.username = savedUser;
            this.loadUserTokenBalance(savedUser);
        } else {
            this.currentUser = null;
            this.isAdmin = false;
            localStorage.removeItem('music_username');
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('music_is_admin');
        }

        if (window.location.pathname === '/') {
            this.loadPublicSongs();
        }
        else if (window.location.pathname.startsWith('/favorites')) {
            this.loadFavoriteSongs();
        }
        else if (window.location.pathname.startsWith('/song/')) {
            const pathParts = window.location.pathname.split('/');
            const songId = pathParts[pathParts.length - 1];
            if (songId) {
                this.loadSingleSongAndComments(songId);
            }
        }
        else if (window.location.pathname === '/orders') {
            this.loadPackages();
            if (this.currentUser) this.loadMyOrders();
        }
        else if (window.location.pathname === '/profile') {
            if (this.currentUser) {
                this.loadProfilePageData();
                this.loadProfileGeneratedSongs();
                this.loadProfileFavorites();
            }
        }

        this.loadSessionPlaylist();
    },
    methods: {
        toggleTheme() {
            this.isDarkMode = !this.isDarkMode;
            const currentTheme = this.isDarkMode ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', currentTheme);
            localStorage.setItem('music_theme', currentTheme);
        },

        loadUserTokenBalance(username) {
            axios.get(`/api/users/${username}/profile`)
                .then(response => {
                    if (response.data && response.data.token_balance !== undefined) {
                        this.userTokens = response.data.token_balance;
                    }
                })
                .catch(error => {
                    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                        this.handleLogout(false);
                    }
                });
        },

        loadPublicSongs() {
            axios.get('/api/songs/public')
                .then(response => { this.publicSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { console.error(error); });
        },

        loadFavoriteSongs() {
            if (!this.currentUser) { window.location.href = '/login'; return; }
            this.isLoadingFavorites = true;
            axios.get('/api/songs/my-favorites')
                .then(response => { this.favoriteSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách yêu thích.' }); })
                .finally(() => { this.isLoadingFavorites = false; });
        },

        loadProfilePageData() {
            axios.get(`/api/users/${this.currentUser}/profile`)
                .then(res => { this.profilePageData = res.data; })
                .catch(err => console.error(err));
        },

        loadProfileGeneratedSongs(loadMore = false) {
            if (!loadMore) {
                this.profileSongPagination.page = 0;
                this.profileGeneratedSongs = [];
            }
            this.isLoadingProfileSongs = true;
            axios.get(`/api/songs/my-songs?page=${this.profileSongPagination.page}&size=${this.profileSongPagination.size}`)
                .then(res => {
                    const data = res.data;
                    const content = data.content ? data.content : Array.isArray(data) ? data : [];

                    if (loadMore) {
                        this.profileGeneratedSongs = this.profileGeneratedSongs.concat(content);
                    } else {
                        this.profileGeneratedSongs = content;
                    }

                    if (data.content) {
                        this.profileSongPagination.hasMore = !data.last;
                        this.profileStats.total = data.totalElements;
                    } else {
                        this.profileSongPagination.hasMore = false;
                        this.profileStats.total = this.profileGeneratedSongs.length;
                    }

                    this.profileStats.completed = this.profileGeneratedSongs.filter(s => s.status === 'COMPLETED').length;
                    this.profileStats.pending = this.profileGeneratedSongs.filter(s => s.status === 'PENDING').length;
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
            if (this.profileFavoriteSongs.length === 0) {
                this.loadProfileFavorites();
            }
        },

        loadProfileFavorites() {
            this.isLoadingProfileFav = true;
            axios.get('/api/songs/my-favorites')
                .then(res => { this.profileFavoriteSongs = Array.isArray(res.data) ? res.data : []; })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileFav = false; });
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
                    this.Toast.fire({ icon: 'success', title: 'Đã bỏ yêu thích bài hát.' });
                })
                .catch(err => this.Toast.fire({ icon: 'error', title: 'Lỗi xử lý.' }));
        },

        generateMusic() {
            if (!this.generationForm.prompt.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!', confirmButtonColor: '#16a34a' });
                return;
            }
            this.isGenerating = true;
            this.generationForm.username = this.currentUser;
            axios.post('/api/songs/generate', this.generationForm)
                .then(response => {
                    const data = response.data;
                    this.Toast.fire({ icon: 'success', title: 'AI đang xử lý giai điệu ngầm...' });
                    this.userTokens = data.remaining_tokens;
                    this.currentTrack = { id: data.songId, title: "AI đang tiến hành xử lý bài hát...", prompt: this.generationForm.prompt, status: "PENDING", audioUrl: "" };
                    this.generationForm.prompt = '';
                    this.isGenerating = false;
                    this.startPollingStatus(data.songId);
                })
                .catch(error => {
                    this.isGenerating = false;
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: error.response && error.response.data ? error.response.data : 'Lỗi kết nối lõi AI.', confirmButtonColor: '#dc3545' });
                });
        },

        playTrack(song) {
			if (this.pollingTimer && this.currentTrack.id === song.id && this.currentTrack.status === 'PENDING') return;
			if (this.pollingTimer) clearInterval(this.pollingTimer);

			this.currentTrack = {
				id: song.id,
				title: song.title,
				prompt: song.prompt,
				status: 'COMPLETED',
				audioUrl: song.audioUrl
			};

			this.$nextTick(() => {
				const audio = document.getElementById('audio-element');
				if (audio) { audio.load(); audio.play(); }
			});
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
				this.Toast.fire({ icon: 'info', title: 'Bài hát đã tồn tại trong playlist' });
			}
		},
		removeTrack(index) {
			this.sessionPlaylist.splice(index, 1);
			sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
			this.Toast.fire({ icon: 'warning', title: 'Đã xóa bài hát khỏi playlist' });
		},
		clearPlaylist() {
			this.sessionPlaylist = [];
			sessionStorage.removeItem('music_session_playlist');
			this.Toast.fire({ icon: 'error', title: 'Đã giải phóng danh sách phát' });
		},

		loadSingleSongAndComments(songId) {
			axios.get(`/api/songs/${songId}/status`)
				.then(response => {
					this.currentTrack = response.data;
					this.loadComments(songId);
				})
				.catch(error => {
					console.error("Không thể tải thông tin bài hát:", error);
					Swal.fire('Lỗi', 'Không tìm thấy bài hát hoặc bạn không có quyền truy cập.', 'error');
				});
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
					if (result.isConfirmed) {
						window.location.href = '/login';
					}
				});
				return;
			}

			const originalLikedState = song.liked_by_me;
			const originalLikeCount = song.total_likes;
			song.liked_by_me = !song.liked_by_me;
			song.total_likes += song.liked_by_me ? 1 : -1;

			if (window.location.pathname.startsWith('/favorites') && !song.liked_by_me) {
				const index = this.favoriteSongs.findIndex(s => s.id === song.id);
				if (index > -1) {
					this.favoriteSongs.splice(index, 1);
				}
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

		handleLogin() {
			if (!this.loginForm.username.trim()) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập tên đăng nhập!' });
				return;
			}
			if (!this.loginForm.password.trim()) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập mật khẩu!' });
				return;
			}

			const btn = document.getElementById('submit-btn');
			if (btn) {
				btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...';
				btn.disabled = true;
			}

			axios.post('/api/auth/login', this.loginForm)
				.then(response => {
					localStorage.setItem('music_username', response.data.username);
					localStorage.setItem('jwt_token', response.data.token);
					localStorage.setItem('music_is_admin', response.data.isAdmin);

					if (btn) {
						btn.innerHTML = '<i class="ti ti-check"></i> Kích hoạt thành công!';
						btn.style.background = '#15803d';
					}

					this.Toast.fire({
						icon: 'success',
						title: `Khởi động hệ thống thành công! Chào mừng ${response.data.username}.`
					});

					setTimeout(() => {
						if (response.data.isAdmin) {
							document.cookie = 'jwt_token=' + response.data.token + '; path=/; max-age=86400; SameSite=Lax';
							window.location.href = '/admin';
						} else {
							window.location.href = '/';
						}
					}, 1000);
				})
				.catch(() => {
					if (btn) {
						btn.innerHTML = '<i class="ti ti-bolt"></i> Kích hoạt hệ thống';
						btn.disabled = false;
					}
					Swal.fire({
						icon: 'error',
						title: 'Đăng nhập thất bại',
						text: 'Tài khoản hoặc mật khẩu không chính xác.',
						confirmButtonColor: '#16a34a'
					});
				});
		},

		handleRegister() {
			if (!this.registerForm.username.trim()) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập tên đăng nhập!' });
				return;
			}
			if (this.registerForm.username.trim().includes(' ')) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Tên đăng nhập không được chứa khoảng trắng!' });
				return;
			}
			if (!this.registerForm.fullname.trim()) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập họ tên!' });
				return;
			}
			if (!this.registerForm.email.trim()) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập email!' });
				return;
			}
			if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.registerForm.email.trim())) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Email không đúng định dạng!' });
				return;
			}
			if (!this.registerForm.password.trim()) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập mật khẩu!' });
				return;
			}
			if (this.registerForm.password.trim().length < 6) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu phải có ít nhất 6 ký tự!' });
				return;
			}
			if (this.registerForm.password !== this.registerForm.confirmPassword) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu xác nhận không trùng khớp!' });
				return;
			}

			const submitData = {
				username: this.registerForm.username,
				fullname: this.registerForm.fullname,
				email: this.registerForm.email,
				password: this.registerForm.password
			};

			axios.post('/api/auth/register', submitData)
				.then(() => {
					Swal.fire({
						icon: 'success',
						title: 'Thành công',
						text: 'Tạo tài khoản thành công! Bạn nhận được 5 Token trải nghiệm.',
						confirmButtonColor: '#16a34a'
					}).then(() => {
						window.location.href = '/login';
					});
				})
				.catch(error => {
					Swal.fire({
						icon: 'error',
						title: 'Lỗi',
						text: error.response && error.response.data ? "Đăng ký thất bại: " + error.response.data : "Tài khoản hoặc Email đã tồn tại."
                    });
                });
        },

        startPollingStatus(songId) {
            if (this.pollingTimer) clearInterval(this.pollingTimer);
            this.pollingTimer = setInterval(() => {
                axios.get(`/api/songs/${songId}/status`)
                    .then(response => {
                        const statusData = response.data;
                        if (this.currentTrack.id === songId) { this.currentTrack.status = statusData.status; }
                        if (statusData.status === 'COMPLETED') {
                            clearInterval(this.pollingTimer);
                            this.currentTrack.title = statusData.title;
                            this.currentTrack.audioUrl = statusData.audio_url;
                            this.loadPublicSongs();
                            if(window.location.pathname === '/profile' && this.profileTab === 'generated') this.loadProfileGeneratedSongs();
                            this.Toast.fire({ icon: 'success', title: `Sinh xong bài: ${statusData.title}!` });
                            this.$nextTick(() => { const audio = document.getElementById('audio-element'); if (audio) { audio.load(); audio.play(); } });
                        } else if (statusData.status === 'FAILED') {
                            clearInterval(this.pollingTimer);
                            Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Quá trình tạo nhạc thất bại!' });
                        }
                    })
                    .catch(() => { clearInterval(this.pollingTimer); });
            }, 3000);
        },

        playTrack(song) {
            if (this.pollingTimer && this.currentTrack.id === song.id && this.currentTrack.status === 'PENDING') return;
            if (this.pollingTimer) clearInterval(this.pollingTimer);
            this.currentTrack = { id: song.id, title: song.title, prompt: song.prompt, status: 'COMPLETED', audioUrl: song.audioUrl };
            this.$nextTick(() => { const audio = document.getElementById('audio-element'); if (audio) { audio.load(); audio.play(); } });
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
                this.Toast.fire({ icon: 'info', title: 'Bài hát đã tồn tại trong playlist' });
            }
        },
        removeTrack(index) {
            this.sessionPlaylist.splice(index, 1);
            sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
            this.Toast.fire({ icon: 'warning', title: 'Đã xóa bài hát khỏi playlist' });
        },
        clearPlaylist() {
            this.sessionPlaylist = [];
            sessionStorage.removeItem('music_session_playlist');
            this.Toast.fire({ icon: 'error', title: 'Đã giải phóng danh sách phát' });
        },

        loadSingleSongAndComments(songId) {
            axios.get(`/api/songs/${songId}/status`)
                .then(response => {
                    this.currentTrack = response.data;
                    this.loadComments(songId);
                })
                .catch(error => { Swal.fire('Lỗi', 'Không tìm thấy bài hát hoặc bạn không có quyền truy cập.', 'error'); });
        },

        toggleLike(song) {
            if (!this.currentUser) {
                Swal.fire({ icon: 'warning', title: 'Yêu cầu đăng nhập', text: 'Bạn cần đăng nhập để "thả tim" cho bài hát này.', confirmButtonText: 'Đăng nhập ngay', showCancelButton: true, cancelButtonColor: '#6e7881', confirmButtonColor: '#16a34a', cancelButtonText: 'Hủy' })
                    .then((result) => { if (result.isConfirmed) { window.location.href = '/login'; } });
                return;
            }
            const originalLikedState = song.liked_by_me;
            const originalLikeCount = song.total_likes;
            song.liked_by_me = !song.liked_by_me;
            song.total_likes += song.liked_by_me ? 1 : -1;

            if (window.location.pathname.startsWith('/favorites') && !song.liked_by_me) {
                const index = this.favoriteSongs.findIndex(s => s.id === song.id);
                if (index > -1) { this.favoriteSongs.splice(index, 1); }
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

        handleLogin() {
            const btn = document.getElementById('submit-btn');
            if (btn) { btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...'; btn.disabled = true; }
            axios.post('/api/auth/login', this.loginForm)
                .then(response => {
                    localStorage.setItem('music_username', response.data.username);
                    localStorage.setItem('jwt_token', response.data.token);
                    localStorage.setItem('music_is_admin', response.data.isAdmin);
                    if (btn) { btn.innerHTML = '<i class="ti ti-check"></i> Kích hoạt thành công!'; btn.style.background = '#15803d'; }
                    this.Toast.fire({ icon: 'success', title: `Khởi động hệ thống thành công! Chào mừng ${response.data.username}.` });
                    setTimeout(() => {
                        if (response.data.isAdmin) {
                            document.cookie = 'jwt_token=' + response.data.token + '; path=/; max-age=86400; SameSite=Lax';
                            window.location.href = '/admin';
                        } else { window.location.href = '/'; }
                    }, 1000);
                })
                .catch(() => {
                    if (btn) { btn.innerHTML = '<i class="ti ti-bolt"></i> Kích hoạt hệ thống'; btn.disabled = false; }
                    Swal.fire({ icon: 'error', title: 'Đăng nhập thất bại', text: 'Tài khoản hoặc mật khẩu không chính xác.', confirmButtonColor: '#16a34a' });
                });
        },

        handleRegister() {
            if (this.registerForm.password !== this.registerForm.confirmPassword) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu xác nhận không trùng khớp!' });
                return;
            }
            const btn = document.querySelector('button[type="submit"]');
            if (btn) { btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang khởi tạo...'; btn.disabled = true; }

            const submitData = { username: this.registerForm.username, fullname: this.registerForm.fullname, email: this.registerForm.email, password: this.registerForm.password };
            axios.post('/api/auth/register', submitData)
                .then(() => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tạo tài khoản thành công! Bạn nhận được 5 Token trải nghiệm.', confirmButtonColor: '#16a34a' })
                        .then(() => { window.location.href = '/login'; });
                })
                .catch(error => {
                    if (btn) { btn.innerHTML = '<i class="ti ti-user-plus"></i> Khởi tạo tài khoản'; btn.disabled = false; }
                    let errorMsg = "Tài khoản hoặc Email đã tồn tại.";
                    if (error.response && error.response.data) { errorMsg = error.response.data.message || error.response.data || errorMsg; }
                    Swal.fire({ icon: 'error', title: 'Đăng ký thất bại', text: errorMsg, confirmButtonColor: '#dc3545' });
                });
        },

        handleLogout(showConfirm = true) {
            const executeLogout = () => {
                localStorage.removeItem('music_username');
                localStorage.removeItem('jwt_token');
                localStorage.removeItem('music_is_admin');
                this.currentUser = null;
                this.isAdmin = false;
                this.userTokens = 0;
                this.generationForm.username = '';
                window.location.href = '/';
            };
            if (!showConfirm) { executeLogout(); return; }
            Swal.fire({ title: 'Xác nhận đăng xuất?', text: "Hệ thống sẽ ngắt kết nối với tài khoản hiện tại.", icon: 'question', showCancelButton: true, confirmButtonColor: '#16a34a', cancelButtonColor: '#d33', confirmButtonText: 'Đăng xuất', cancelButtonText: 'Hủy' })
                .then((result) => { if (result.isConfirmed) { executeLogout(); } });
        },

        openProfileModal() {
            this.profileModalTab = 'info';
            this.changePasswordForm = { oldPassword: '', newPassword: '', confirmNewPassword: '' };
            if (!this.currentUser) return;
            axios.get(`/api/users/${this.currentUser}/profile`)
                .then(response => {
                    const data = response.data;
                    this.profileForm.fullname = data.fullname || '';
                    this.profileForm.email = data.email || '';
                    this.profileForm.photo = data.photo || '';
                    this.showProfileModal = true;
                })
                .catch(error => { Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể tải thông tin cá nhân' }); });
        },

        closeProfileModal() { this.showProfileModal = false; },

        submitUpdateProfile() {
            axios.put(`/api/users/${this.currentUser}/profile`, this.profileForm)
                .then(response => {
                    this.Toast.fire({ icon: 'success', title: 'Cập nhật hồ sơ thành công!' });
                    if (window.location.pathname === '/profile') this.loadProfilePageData();
                    this.showProfileModal = false;
                })
                .catch(error => { Swal.fire({ icon: 'error', title: 'Cập nhật thất bại', text: error.response && error.response.data ? (error.response.data.message || 'Lỗi dữ liệu') : 'Vui lòng kiểm tra lại thông tin.' }); });
        },

        submitChangePassword() {
            if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmNewPassword) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu mới và mật khẩu xác nhận không trùng khớp!' });
                return;
            }
            const payload = { oldPassword: this.changePasswordForm.oldPassword, newPassword: this.changePasswordForm.newPassword };
            axios.put(`/api/users/${this.currentUser}/change-password`, payload)
                .then(response => {
                    this.showProfileModal = false;
                    Swal.fire({ icon: 'success', title: 'Thành công!', text: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.', confirmButtonColor: '#16a34a' })
                        .then(() => { this.handleLogout(false); });
                })
                .catch(error => { Swal.fire({ icon: 'error', title: 'Đổi mật khẩu thất bại', text: error.response && error.response.data ? error.response.data.message : 'Đã có lỗi xảy ra.' }); });
        },

        loadPackages() {
            this.isLoadingPackages = true;
            axios.get('/api/packages')
                .then(res => { this.packages = res.data; this.isLoadingPackages = false; })
                .catch(() => { this.isLoadingPackages = false; });
        },

        loadMyOrders() {
            if (!this.currentUser) return;
            this.isLoadingOrders = true;
            axios.get('/api/orders/my-orders')
                .then(res => { this.myOrders = Array.isArray(res.data) ? res.data : []; this.isLoadingOrders = false; })
                .catch(() => { this.isLoadingOrders = false; });
        },

        buyPackage(pkg) {
            if (!this.currentUser) { window.location.href = '/login'; return; }
            Swal.fire({
                title: 'Xác nhận mua gói?',
                html: `<b>${pkg.name}</b><br>${pkg.tokens} token — <b>${this.formatPrice(pkg.price)}đ</b><br><br><span style="font-size: 13px; color: #6e7881;">Hệ thống sẽ chuyển hướng sang VNPAY.</span>`,
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Đến trang thanh toán',
                cancelButtonText: 'Huỷ',
                confirmButtonColor: '#16a34a'
            }).then(result => {
                if (!result.isConfirmed) return;
                Swal.fire({ title: 'Đang chuyển hướng...', allowOutsideClick: false, didOpen: () => { Swal.showLoading(); } });
                axios.post('/api/orders/create', { package_id: pkg.id })
                    .then(res => {
                        if (res.data.paymentUrl) { window.location.href = res.data.paymentUrl; }
                        else { Swal.fire('Lỗi', 'Không thể tạo phiên thanh toán', 'error'); }
                    })
                    .catch(err => { Swal.fire('Lỗi', err.response?.data?.message || 'Có lỗi xảy ra khi kết nối máy chủ!', 'error'); });
            });
        },

        formatPrice(price) {
            if (!price) return '0';
            return price.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
        },

        formatDate(dateStr) {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
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
            if (!this.newComment.content.trim()) { this.Toast.fire({ icon: 'warning', title: 'Vui lòng nhập nội dung.' }); return; }
            const payload = { content: this.newComment.content, parent_id: parentId };
            axios.post(`/api/songs/${songId}/comments`, payload)
                .then(response => {
                    if (parentId) {
                        const parentComment = this.commentPagination.content.find(c => c.id === parentId);
                        if (parentComment) { parentComment.replies.push(response.data); }
                    } else {
                        this.commentPagination.content.unshift(response.data);
                        this.commentPagination.totalElements++;
                    }
                    this.newComment.content = '';
                    this.replyingToCommentId = null;
                    this.Toast.fire({ icon: 'success', title: 'Đã gửi bình luận!' });
                })
                .catch(error => { Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể gửi bình luận.' }); });
        },

        toggleReplyForm(commentId) {
            this.replyingToCommentId = (this.replyingToCommentId === commentId) ? null : commentId;
            this.newComment.content = '';
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

        formatRelativeTime(dateString) {
            const date = new Date(dateString); const now = new Date(); const seconds = Math.round((now - date) / 1000);
            const minutes = Math.round(seconds / 60); const hours = Math.round(minutes / 60); const days = Math.round(hours / 24);
            if (seconds < 60) return `${seconds} giây trước`;
            if (minutes < 60) return `${minutes} phút trước`;
            if (hours < 24) return `${hours} giờ trước`;
            if (days < 7) return `${days} ngày trước`;
            return date.toLocaleDateString('vi-VN');
        },

        copyText(text) {
            navigator.clipboard.writeText(text).then(() => { this.Toast.fire({ icon: 'success', title: 'Đã copy!' }); });
        },

        cancelOrder() {
            Swal.fire({ title: 'Huỷ đơn hàng?', text: 'Bạn có chắc muốn huỷ đơn này không?', icon: 'warning', showCancelButton: true, confirmButtonText: 'Huỷ đơn', cancelButtonText: 'Giữ lại', confirmButtonColor: '#dc3545' })
                .then(result => { if (result.isConfirmed) window.location.href = '/orders'; });
        },
        goToOrders() { window.location.href = '/orders'; }
    }
});