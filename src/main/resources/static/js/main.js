new Vue({
	el: '#app',
	data: {
		// QUẢN LÝ THEO DÕI CHẾ ĐỘ SÁNG / TỐI ĐỒNG BỘ LAYOUT VÀ INDEX
		isDarkMode: localStorage.getItem('music_theme') !== 'light',

		currentUser: null,           // Gán null ban đầu để ẩn giao diện tài khoản khi chưa login
		isAdmin: false,              // Trạng thái admin để hiển thị link quản trị
		userTokens: 0,               // Token thực tế từ database
		publicSongs: [],             // Danh sách nhạc public dạng mảng phẳng
		sessionPlaylist: [],         // Playlist tạm thời lưu trong Session Storage của Guest
		favoriteSongs: [],           // Danh sách bài hát yêu thích
		isLoadingFavorites: false,   // Trạng thái tải cho trang yêu thích
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

		// Cấu trúc Form đăng nhập
		loginForm: {
			username: '',
			password: ''
		},

		registerForm: {
			username: '',
			fullname: '',
			email: '',
			password: '',
			confirmPassword: ''
		},
		filters: { keyword: '' },
		pollingTimer: null,

		// Trạng thái hiển thị modal và dữ liệu form Hồ sơ (Của Hòa)
		profileModalTab: 'info', // 'info' hoặc 'password'
		showProfileModal: false,
		profileForm: {
			fullname: '',
			email: '',
			photo: ''
		},

		// Form đổi mật khẩu (Của Hòa)
		changePasswordForm: {
			oldPassword: '',
			newPassword: '',
			confirmNewPassword: ''
		}
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
		},
	},
	created() {
		// TỰ ĐỘNG GẮN JWT VÀO TẤT CẢ REQUEST CỦA AXIOS (Khắc phục lỗi bảo mật)
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
		// Khởi tạo Toast cấu hình SweetAlert2 mịn ở góc màn hình
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

		const savedUser = localStorage.getItem('music_username');
		const savedToken = localStorage.getItem('jwt_token');

		// Xác thực bằng JWT thay vì gọi API Check Session
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

		// Tải các tài nguyên công khai sau khi đã xác định trạng thái đăng nhập
		// Phân luồng tải dữ liệu theo trang hiện tại để tối ưu
		if (window.location.pathname === '/') {
			this.loadPublicSongs();
		} else if (window.location.pathname.startsWith('/favorites')) {
			this.loadFavoriteSongs();
		}
		// Nếu đang ở trang orders
		if (window.location.pathname === '/orders') {
			this.loadPackages();
			if (this.currentUser) this.loadMyOrders();
		}
		// Playlist tạm luôn được tải
		this.loadSessionPlaylist();
	},
	methods: {
		// HÀM CHUYỂN ĐỔI CHẾ ĐỘ SÁNG / TỐI ĐỒNG BỘ TOÀN HỆ THỐNG
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
					console.error("Lỗi đồng bộ dữ liệu token cá nhân:", error);
					// Nếu token bị lỗi 401 hoặc 403 (hết hạn), tự động logout ra
					if (error.response && (error.response.status === 401 || error.response.status === 403)) {
						this.handleLogout(false);
					}
				});
		},

		loadPublicSongs() {
			axios.get('/api/songs/public')
				.then(response => {
					// Backend đã trả về đầy đủ thông tin 'total_likes' và 'liked_by_me'
					this.publicSongs = Array.isArray(response.data) ? response.data : [];
				})
				.catch(error => {
					console.error("Lỗi khi tải kho nhạc public:", error);
				});
		},

		loadFavoriteSongs() {
			if (!this.currentUser) {
				window.location.href = '/login'; // Chuyển hướng đến trang đăng nhập nếu chưa đăng nhập
				return;
			}
			this.isLoadingFavorites = true;
			axios.get('/api/songs/my-favorites')
				.then(response => {
					// Backend đã trả về đầy đủ thông tin, chỉ cần gán trực tiếp
					this.favoriteSongs = Array.isArray(response.data) ? response.data : [];
				})
				.catch(error => {
					console.error("Lỗi khi tải danh sách yêu thích:", error);
					this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách yêu thích.' });
				})
				.finally(() => { this.isLoadingFavorites = false; });
		},

		generateMusic() {
			if (!this.generationForm.prompt.trim()) {
				Swal.fire({
					icon: 'warning',
					title: 'Thiếu thông tin',
					text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!',
					confirmButtonColor: '#16a34a'
				});
				return;
			}

			this.isGenerating = true;
			this.generationForm.username = this.currentUser;

			// Header JWT đã tự động đính kèm nhờ Axios Interceptor ở created()
			axios.post('/api/songs/generate', this.generationForm)
				.then(response => {
					const data = response.data;
					this.Toast.fire({ icon: 'success', title: 'AI đang xử lý giai điệu ngầm...' });
					this.userTokens = data.remaining_tokens;

					this.currentTrack = {
						id: data.songId,
						title: "AI đang tiến hành xử lý bài hát...",
						prompt: this.generationForm.prompt,
						status: "PENDING",
						audioUrl: ""
					};

					this.generationForm.prompt = '';
					this.isGenerating = false;
					this.startPollingStatus(data.songId);
				})
				.catch(error => {
					this.isGenerating = false;
					Swal.fire({
						icon: 'error',
						title: 'Thất bại',
						text: error.response && error.response.data ? error.response.data : 'Lỗi kết nối lõi AI.',
						confirmButtonColor: '#dc3545'
					});
				});
		},

		startPollingStatus(songId) {
			if (this.pollingTimer) clearInterval(this.pollingTimer);

			this.pollingTimer = setInterval(() => {
				axios.get(`/api/songs/${songId}/status`)
					.then(response => {
						const statusData = response.data;
						if (this.currentTrack.id === songId) {
							this.currentTrack.status = statusData.status;
						}

						if (statusData.status === 'COMPLETED') {
							clearInterval(this.pollingTimer);
							this.currentTrack.title = statusData.title;
							this.currentTrack.audioUrl = statusData.audio_url;
							this.loadPublicSongs();

							this.Toast.fire({ icon: 'success', title: `Sinh xong bài: ${statusData.title}!` });

							this.$nextTick(() => {
								const audio = document.getElementById('audio-element');
								if (audio) { audio.load(); audio.play(); }
							});
						} else if (statusData.status === 'FAILED') {
							clearInterval(this.pollingTimer);
							Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Quá trình tạo nhạc thất bại!' });
						}
					})
					.catch(() => {
						clearInterval(this.pollingTimer);
					});
			}, 3000);
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

			// Cập nhật giao diện ngay lập tức để tăng trải nghiệm người dùng (Optimistic Update)
			const originalLikedState = song.liked_by_me;
			const originalLikeCount = song.total_likes;
			song.liked_by_me = !song.liked_by_me;
			song.total_likes += song.liked_by_me ? 1 : -1;

			// Nếu đang ở trang Yêu thích và người dùng "Bỏ thích"
			// thì xóa bài hát đó khỏi danh sách `favoriteSongs`
			if (window.location.pathname.startsWith('/favorites') && !song.liked_by_me) {
				const index = this.favoriteSongs.findIndex(s => s.id === song.id);
				if (index > -1) {
					this.favoriteSongs.splice(index, 1);
				}
			}

			// Gọi API để cập nhật backend
			axios.post(`/api/songs/${song.id}/like`)
				.then(response => {
					// Cập nhật lại dữ liệu từ phản hồi của API để đảm bảo tính chính xác
					song.liked_by_me = response.data.liked;
					song.total_likes = response.data.total_likes;
					this.Toast.fire({ icon: 'success', title: response.data.message });
				})
				.catch(error => {
					// Nếu có lỗi, khôi phục lại trạng thái ban đầu
					song.liked_by_me = originalLikedState;
					song.total_likes = originalLikeCount;

					// Nếu đang ở trang Yêu thích và thao tác lỗi, thêm lại bài hát vào danh sách
					if (window.location.pathname.startsWith('/favorites') && song.liked_by_me) {
						const isExist = this.favoriteSongs.some(s => s.id === song.id);
						if (!isExist) {
							this.favoriteSongs.push(song);
						}
					}
					this.Toast.fire({ icon: 'error', title: error.response?.data?.message || 'Đã có lỗi xảy ra' });
				});
		},

		handleLogin() {
			const btn = document.getElementById('submit-btn');
			if (btn) {
				btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...';
				btn.disabled = true;
			}

			axios.post('/api/auth/login', this.loginForm)
				.then(response => {
					// LƯU CẢ USERNAME VÀ JWT TOKEN ĐỂ CHẠY LUỒNG HEAD
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

		handleLogout(showConfirm = true) {
			const executeLogout = () => {
				// XÓA ĐỒNG THỜI CẢ USERNAME VÀ JWT TOKEN
				localStorage.removeItem('music_username');
				localStorage.removeItem('jwt_token');
				localStorage.removeItem('music_is_admin');
				this.currentUser = null;
				this.isAdmin = false;
				this.userTokens = 0;
				this.generationForm.username = '';
				window.location.href = '/';
			};

			if (!showConfirm) {
				executeLogout();
				return;
			}

			Swal.fire({
				title: 'Xác nhận đăng xuất?',
				text: "Hệ thống sẽ ngắt kết nối với tài khoản hiện tại.",
				icon: 'question',
				showCancelButton: true,
				confirmButtonColor: '#16a34a',
				cancelButtonColor: '#d33',
				confirmButtonText: 'Đăng xuất',
				cancelButtonText: 'Hủy'
			}).then((result) => {
				if (result.isConfirmed) {
					executeLogout();
				}
			});
		},

		// --- CÁC HÀM QUẢN LÝ HỒ SƠ CÁ NHÂN (CỦA HÒA) ---
		openProfileModal() {
			// Reset về trạng thái mặc định mỗi khi mở
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
				.catch(error => {
					Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể tải thông tin cá nhân' });
				});
		},
		// Lấy danh sách gói token
		loadPackages() {
			this.isLoadingPackages = true;
			axios.get('/api/packages')
				.then(res => {
					this.packages = res.data;
					this.isLoadingPackages = false;
				})
				.catch(() => { this.isLoadingPackages = false; });
		},

		// Lấy lịch sử mua hàng
		loadMyOrders() {
			if (!this.currentUser) return;
			this.isLoadingOrders = true;
			axios.get('/api/orders/my-orders', {
				headers: { Authorization: 'Bearer ' + localStorage.getItem('jwt_token') }
			})
				.then(res => {
					this.myOrders = Array.isArray(res.data) ? res.data : [];
					this.isLoadingOrders = false;
				})
				.catch(() => { this.isLoadingOrders = false; });
		},

		// Mua gói token
		buyPackage(pkg) {
			if (!this.currentUser) {
				window.location.href = '/login';
				return;
			}

			Swal.fire({
				title: 'Xác nhận mua gói?',
				html: `<b>${pkg.name}</b><br>${pkg.tokens} token — <b>${this.formatPrice(pkg.price)}đ</b>`,
				icon: 'question',
				showCancelButton: true,
				confirmButtonText: 'Mua ngay',
				cancelButtonText: 'Huỷ',
				confirmButtonColor: '#16a34a'
			}).then(result => {
				if (!result.isConfirmed) return;

				axios.post('/api/orders/create',
					{ package_id: pkg.id },
					{ headers: { Authorization: 'Bearer ' + localStorage.getItem('jwt_token') } }
				)
					.then(res => {
						const orderCode = res.data.order_code;

						// Giả lập thanh toán thành công (trong thực tế sẽ redirect sang VNPAY/Momo)
						return axios.post(`/api/orders/confirm/${orderCode}`, {}, {
							headers: { Authorization: 'Bearer ' + localStorage.getItem('jwt_token') }
						});
					})
					.then(res => {
						Swal.fire({
							title: 'Mua thành công!',
							html: `Đã cộng <b class="text-success">${pkg.tokens} token</b> vào tài khoản.<br>Số dư hiện tại: <b>${res.data.new_balance} token</b>`,
							icon: 'success',
							confirmButtonColor: '#16a34a'
						});

						// Cập nhật lại token hiển thị trên navbar + reload lịch sử
						this.userTokens = res.data.new_balance;
						this.loadMyOrders();
					})
					.catch(err => {
						Swal.fire('Lỗi', err.response?.data?.message || 'Có lỗi xảy ra!', 'error');
					});
			});
		},

		// Format giá tiền
		formatPrice(price) {
			if (!price) return '0';
			return price.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
		},

		// Format ngày giờ
		formatDate(dateStr) {
			if (!dateStr) return '';
			const d = new Date(dateStr);
			return d.toLocaleDateString('vi-VN') + ' ' +
				d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
		},
		closeProfileModal() {
			this.showProfileModal = false;
		},

		submitUpdateProfile() {
			axios.put(`/api/users/${this.currentUser}/profile`, this.profileForm)
				.then(response => {
					this.Toast.fire({ icon: 'success', title: 'Cập nhật hồ sơ thành công!' });
					this.showProfileModal = false;
				})
				.catch(error => {
					Swal.fire({
						icon: 'error',
						title: 'Cập nhật thất bại',
						text: error.response && error.response.data ? (error.response.data.message || 'Lỗi dữ liệu') : 'Vui lòng kiểm tra lại thông tin.'
					});
				});
		},

		submitChangePassword() {
			if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmNewPassword) {
				Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu mới và mật khẩu xác nhận không trùng khớp!' });
				return;
			}

			const payload = {
				oldPassword: this.changePasswordForm.oldPassword,
				newPassword: this.changePasswordForm.newPassword
			};

			axios.put(`/api/users/${this.currentUser}/change-password`, payload)
				.then(response => {
					this.showProfileModal = false; // Đóng popup modal ngay lập tức
					Swal.fire({
						icon: 'success',
						title: 'Thành công!',
						text: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.',
						confirmButtonColor: '#16a34a'
					}).then(() => {
						// Gọi logout không cần hỏi
						this.handleLogout(false);
					});
				})
				.catch(error => {
					Swal.fire({
						icon: 'error',
						title: 'Đổi mật khẩu thất bại',
						text: error.response && error.response.data ? error.response.data.message : 'Đã có lỗi xảy ra.'
					});
				});
		},
		// Giả lập xác nhận thanh toán
		simulatePayment() {
			const orderCode = this.sandboxOrder.order_code;
			axios.post(`/api/orders/confirm/${orderCode}`, {}, {
				headers: { Authorization: 'Bearer ' + localStorage.getItem('jwt_token') }
			})
				.then(res => {
					this.sandboxStatus = 'SUCCESS';
					this.userTokens = res.data.new_balance;
				})
				.catch(err => {
					Swal.fire('Lỗi', err.response?.data?.message || 'Có lỗi xảy ra!', 'error');
				});
		},

		// Copy text vào clipboard
		copyText(text) {
			navigator.clipboard.writeText(text).then(() => {
				Swal.fire({
					toast: true, position: 'top-end', icon: 'success',
					title: 'Đã copy!', showConfirmButton: false, timer: 1500
				});
			});
		},	
		// Huỷ đơn hàng
		cancelOrder() {
			Swal.fire({
				title: 'Huỷ đơn hàng?',
				text: 'Bạn có chắc muốn huỷ đơn này không?',
				icon: 'warning',
				showCancelButton: true,
				confirmButtonText: 'Huỷ đơn',
				cancelButtonText: 'Giữ lại',
				confirmButtonColor: '#dc3545'
			}).then(result => {
				if (result.isConfirmed) window.location.href = '/orders';
			});
		},

		goToOrders() {
			window.location.href = '/orders';
		},
	}
});