new Vue({
    el: '#app',
    data: {
        // QUẢN LÝ THEO DÕI CHẾ ĐỘ SÁNG / TỐI ĐỒNG BỘ LAYOUT VÀ INDEX
        isDarkMode: localStorage.getItem('music_theme') !== 'light',
        currentPage: window.location.pathname,

        currentUser: null,
        userPhoto: null,
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
        forgotPasswordForm: { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false },
        filters: { keyword: '' },
        workspaceFilters: {
            liked: false,
            public: false,
            private: false,
            pending: false
        },
        workspaceSortOption: 'newest',
        sortLabels: {
            newest: 'Newest',
            oldest: 'Oldest',
            most_liked: 'Most Liked',
            least_liked: 'Least Liked'
        },
        pollingTimer: null,
        showQueue: false,
        uploadingSongId: null,
        isPlaying: false,
        isOnSongDetailPage: false,
        profileUsername: '',
        isFollowing: false,
        followersCount: 0,
        followingCount: 0,
        editingSongForm: { id: null, title: '', prompt: '', isPublic: false, coverUrl: '' },
        isSavingSongEdit: false,

        profileModalTab: 'info',
        showProfileModal: false,
        profileModalError: '',
        profileForm: { fullname: '', email: '', photo: '' },
        changePasswordForm: { oldPassword: '', newPassword: '', confirmNewPassword: '' },

        commentPagination: { content: [], number: 0, totalPages: 1, totalElements: 0 },
        isLoadingComments: false,
        isSubmittingComment: false,
        newComment: { content: '' },
        newReply: { content: '' },
        replyingToCommentId: null,
        editingComment: null,

        // ================= DATA CHO TRANG PROFILE =================
        profilePageData: {}, // Chứa thông tin user cho trang profile
        profileStats: { total: 0, completed: 0, pending: 0, totalFavorites: 0 }, // Thống kê
        profileTab: 'generated', // 'generated' hoặc 'favorites'
        profileGeneratedSongs: [], // Danh sách nhạc đã tạo
        profileFavoriteSongs: [], // Danh sách nhạc yêu thích ở profile
        isLoadingProfileSongs: false,
        isLoadingProfileFav: false,
        profileSongPagination: { page: 0, size: 10, hasMore: false },

        // ================= DATA CHO BOXCHAT =================
        chatOpen: false,
        chatContacts: [],
        activeChatUser: null,
        chatMessages: [],
        chatInput: '',
        chatSearchQuery: '',
        chatSearchResults: [],
        stompClient: null,
        totalUnreadCount: 0,
        chatSearchTimeout: null,

        // ================= TÌM KIẾM CREATOR (EXPLORE) =================
        matchingCreators: [],
        creatorSearchTimeout: null
    },
    watch: {
        'filters.keyword': function (newVal) {
            if (this.creatorSearchTimeout) clearTimeout(this.creatorSearchTimeout);
            if (!newVal || !newVal.trim()) {
                this.matchingCreators = [];
                return;
            }
            this.creatorSearchTimeout = setTimeout(() => {
                axios.get('/api/users/search?query=' + encodeURIComponent(newVal.trim()))
                    .then(response => {
                        this.matchingCreators = response.data || [];
                    })
                    .catch(err => {
                        console.error('Lỗi tìm kiếm creator:', err);
                        this.matchingCreators = [];
                    });
            }, 300);
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
        forYouSongs() {
            if (this.currentUser && this.profileGeneratedSongs.length > 0) {
                return this.profileGeneratedSongs.slice(0, 5);
            }
            return this.publicSongs.slice(0, 5);
        },
        studioSongs() {
            const studio = this.publicSongs.filter(s => s.username);
            return studio.length > 0 ? studio.slice(0, 5) : this.publicSongs.slice(0, 5);
        },
        bestSongs() {
            return [...this.publicSongs].sort((a, b) => {
                const getLikes = (song) => {
                    const id = song.id;
                    if (id > 10) return (song.total_likes || 0);
                    const seedLikes = (id * 23 + 17) % 80 + 10;
                    return (seedLikes + (song.total_likes || 0)) * 1000;
                };
                return getLikes(b) - getLikes(a);
            }).slice(0, 5);
        },
        activeFiltersCount() {
            let count = 0;
            if (this.workspaceFilters.liked) count++;
            if (this.workspaceFilters.public) count++;
            if (this.workspaceFilters.private) count++;
            if (this.workspaceFilters.pending) count++;
            return count;
        },
        filteredProfileSongs() {
            let result = [...this.profileGeneratedSongs];
            if (this.filters.keyword && this.filters.keyword.trim() !== '') {
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw))
                );
            }
            
            const activeOptions = [];
            if (this.workspaceFilters.public) activeOptions.push('PUBLIC');
            if (this.workspaceFilters.private) activeOptions.push('PRIVATE');
            
            if (activeOptions.length > 0) {
                result = result.filter(s => activeOptions.includes(s.visibility));
            }
            
            if (this.workspaceFilters.pending) {
                result = result.filter(s => s.status === 'PENDING');
            }
            
            if (this.workspaceFilters.liked) {
                result = result.filter(s => this.profileFavoriteSongs.some(fav => (fav.song && fav.song.id === s.id) || fav.songId === s.id));
            }
            
            if (this.workspaceSortOption === 'newest') {
                result.sort((a, b) => new Date(b.created_at || b.createdAt || 0) - new Date(a.created_at || a.createdAt || 0));
            } else if (this.workspaceSortOption === 'oldest') {
                result.sort((a, b) => new Date(a.created_at || a.createdAt || 0) - new Date(b.created_at || b.createdAt || 0));
            } else if (this.workspaceSortOption === 'most_liked') {
                const getLikes = (song) => {
                    const baseLikes = song.id > 10 ? 0 : ((song.id * 23 + 17) % 80 + 10) * 1000;
                    return baseLikes + (song.total_likes || song.totalLikes || 0);
                };
                result.sort((a, b) => getLikes(b) - getLikes(a));
            } else if (this.workspaceSortOption === 'least_liked') {
                const getLikes = (song) => {
                    const baseLikes = song.id > 10 ? 0 : ((song.id * 23 + 17) % 80 + 10) * 1000;
                    return baseLikes + (song.total_likes || song.totalLikes || 0);
                };
                result.sort((a, b) => getLikes(a) - getLikes(b));
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

        axios.interceptors.response.use(response => {
            const contentType = response.headers['content-type'];
            if (contentType && contentType.includes('text/html') && response.config.url.includes('/api/')) {
                localStorage.removeItem('jwt_token');
                localStorage.removeItem('music_username');
                localStorage.removeItem('music_is_admin');
                window.location.href = '/login?error=expired';
                return Promise.reject(new Error('Session expired'));
            }
            return response;
        }, error => {
            if (error.response && error.response.status === 401) {
                localStorage.removeItem('jwt_token');
                localStorage.removeItem('music_username');
                localStorage.removeItem('music_is_admin');
                window.location.href = '/login?error=expired';
            }
            return Promise.reject(error);
        });
    },
    mounted() {
        this.isOnSongDetailPage = window.location.pathname.startsWith('/song/');
        if (this.isOnSongDetailPage) {
            const style = document.createElement('style');
            style.innerHTML = '.suno-sticky-player { display: none !important; }';
            document.head.appendChild(style);
        }
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

        // XỬ LÝ THÔNG BÁO VNPAY
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

        // XỬ LÝ ĐĂNG NHẬP OAUTH2 GOOGLE
        const tokenParam = urlParams.get('token');
        const userParam = urlParams.get('username');
        const isAdminParam = urlParams.get('isAdmin');
        if (tokenParam && userParam) {
            localStorage.setItem('jwt_token', tokenParam);
            localStorage.setItem('music_username', userParam);
            localStorage.setItem('music_is_admin', isAdminParam === 'true');
            window.history.replaceState(null, null, window.location.pathname);
            this.Toast.fire({ icon: 'success', title: `Chào mừng ${userParam} đã đăng nhập!` });
        }

        const savedUser = localStorage.getItem('music_username');
        const savedToken = localStorage.getItem('jwt_token');

        if (savedUser && savedToken) {
            this.currentUser = savedUser;
            this.isAdmin = localStorage.getItem('music_is_admin') === 'true';
            this.generationForm.username = savedUser;
            this.loadUserTokenBalance(savedUser);

            // Đồng bộ JWT cookie cho WebSocket handshake
            document.cookie = "jwt_token=" + savedToken + ";path=/;max-age=86400;SameSite=Lax";
            setTimeout(() => {
                this.connectWebSocket();
                this.loadRecentChats();
                this.loadTotalUnreadCount();
            }, 600);
        } else {
            this.currentUser = null;
            this.isAdmin = false;
            localStorage.removeItem('music_username');
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('music_is_admin');
        }

        // PHÂN LUỒNG TRANG
        if (window.location.pathname === '/' || window.location.pathname === '/home') {
            this.loadPublicSongs();
        }
        else if (window.location.pathname === '/explore') {
            this.loadPublicSongs();
        }
        else if (window.location.pathname === '/create') {
            if (this.currentUser) {
                this.profileUsername = this.currentUser;
                this.loadProfileGeneratedSongs();
                this.loadProfileFavorites();
                
                // Prefill and auto-create from Home prompt box
                const promptParam = urlParams.get('prompt');
                if (promptParam) {
                    this.generationForm.prompt = promptParam;
                    const autoParam = urlParams.get('auto');
                    if (autoParam === 'true') {
                        setTimeout(() => {
                            this.generateMusic();
                        }, 600);
                    }
                }
            }
        }
        else if (window.location.pathname.startsWith('/favorites')) {
            this.loadFavoriteSongs();
        }
        else if (window.location.pathname.startsWith('/song/')) {
            const pathParts = window.location.pathname.split('/');
            const songId = pathParts[pathParts.length - 1];
            if (songId) {
                this.loadSingleSongAndComments(songId);
                this.loadPublicSongs();
            }
        }
        else if (window.location.pathname === '/orders') {
            this.loadPackages();
            if (this.currentUser) this.loadMyOrders();
        }
        // ================= LUỒNG LOAD CHO TRANG PROFILE =================
        else if (window.location.pathname === '/profile') {
            const urlParams = new URLSearchParams(window.location.search);
            const userParam = urlParams.get('u');
            this.profileUsername = userParam || this.currentUser;

            if (this.profileUsername) {
                this.loadProfilePageData();
                this.loadProfileGeneratedSongs();
                if (this.currentUser && this.profileUsername === this.currentUser) {
                    this.loadProfileFavorites();
                } else {
                    this.profileTab = 'generated'; // force public tab
                }
                this.loadFollowStatus();
            }
        }

        this.loadSessionPlaylist();
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
            if (!url || url.trim() === '' || url.includes('/images/default-avatar.png')) {
                const displayName = name || this.currentUser || '?';
                return `https://ui-avatars.com/api/?name=${encodeURIComponent(displayName)}&background=16a34a&color=fff&rounded=true`;
            }
            if (url.includes('ui-avatars.com') && !url.includes('rounded=true')) {
                return url + '&rounded=true';
            }
            return url;
        },

        getSongCover(song) {
            if (!song) return 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=80';
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
                
                // Cập nhật trong publicSongs
                const song = this.publicSongs.find(s => s.id === songId);
                if (song) {
                    Vue.set(song, 'coverUrl', coverUrl);
                }
                
                // Cập nhật trong currentTrack
                if (this.currentTrack.id === songId) {
                    Vue.set(this.currentTrack, 'coverUrl', coverUrl);
                }
                
                // Cập nhật trong sessionPlaylist
                const playlistSong = this.sessionPlaylist.find(s => s.id === songId);
                if (playlistSong) {
                    Vue.set(playlistSong, 'coverUrl', coverUrl);
                }
                
                // Cập nhật trong favoriteSongs
                const favSong = this.favoriteSongs.find(s => s.id === songId);
                if (favSong) {
                    Vue.set(favSong, 'coverUrl', coverUrl);
                }
                
                // Cập nhật trong danh sách profile
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
                            if (this.currentTrack.id === songId) {
                                this.currentTrack.title = newTitle;
                            }
                            
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
            window.location.href = `/song/${songId}`;
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
                    this.Toast.fire({ icon: 'success', title: res.data.message });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: err.response?.data?.message || 'Có lỗi xảy ra!' });
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
            const id = typeof song === 'object' ? song.id : song;
            const realListens = typeof song === 'object' ? (song.listenCount || 0) : 0;
            if (id <= 8) {
                const seedPlays = (id * 73 + 124) % 800 + 50;
                return (seedPlays + realListens) + 'K';
            }
            return realListens + ' lượt nghe';
        },

        getLikesCount(song) {
            if (!song) return '0';
            const id = typeof song === 'object' ? song.id : song;
            if (id > 10) {
                return (song.total_likes || 0);
            }
            const seedLikes = (id * 23 + 17) % 80 + 10;
            return (seedLikes + (song.total_likes || 0)) + 'K';
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
                        if (response.data.token_balance !== undefined) {
                            this.userTokens = response.data.token_balance;
                        }
                        if (response.data.photo) {
                            this.userPhoto = response.data.photo;
                        }
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

        // ================= CÁC HÀM CHO TRANG PROFILE =================
        triggerAvatarUpload() {
            if (this.$refs.avatarFileInput) {
                this.$refs.avatarFileInput.click();
            } else {
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
                if (this.profilePageData) {
                    this.profilePageData.photo = newPhoto;
                }
                if (this.profileForm) {
                    this.profileForm.photo = newPhoto;
                }
                Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh đại diện mới thành công!' });
            })
            .catch(err => {
                let msg = 'Tải ảnh đại diện thất bại!';
                if (err.response && err.response.data && err.response.data.message) {
                    msg = err.response.data.message;
                }
                Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
            });
        },

        loadProfilePageData() {
            axios.get(`/api/users/${this.currentUser}/profile`)
                .then(res => {
                    this.profilePageData = res.data;
                    if (res.data.total_songs !== undefined) {
                        this.profileStats.total = res.data.total_songs;
                        this.profileStats.completed = res.data.completed_songs;
                        this.profileStats.pending = res.data.pending_songs;
                    }
                    if (res.data.total_favorites !== undefined) {
                        this.profileStats.totalFavorites = res.data.total_favorites;
                    }
                })
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
                        if (this.profileStats.total === 0 || !loadMore) {
                            this.profileStats.total = data.totalElements;
                        }
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
            if (this.profileFavoriteSongs.length === 0) {
                this.loadProfileFavorites();
            }
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
                    if (this.profileStats.totalFavorites > 0) {
                        this.profileStats.totalFavorites--;
                    }
                    this.Toast.fire({ icon: 'success', title: 'Đã bỏ yêu thích bài hát.' });
                })
                .catch(err => this.Toast.fire({ icon: 'error', title: 'Lỗi xử lý.' }));
        },
        // ================= KẾT THÚC CÁC HÀM TRANG PROFILE =================

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
                    this.currentTrack = { id: data.songId, title: "AI đang tiến hành xử lý bài hát...", prompt: this.generationForm.prompt, status: "PENDING", audioUrl: "", username: this.currentUser };
                    
                    if (window.location.pathname === '/' || window.location.pathname === '/profile') {
                        this.profileGeneratedSongs.unshift({
                            id: data.songId,
                            title: "AI đang tiến hành xử lý bài hát...",
                            prompt: this.generationForm.prompt,
                            status: "PENDING",
                            audioUrl: "",
                            username: this.currentUser,
                            created_at: new Date().toISOString()
                        });
                    }
                    
                    this.generationForm.prompt = '';
                    this.isGenerating = false;
                    this.startPollingStatus(data.songId);
                })
                .catch(error => {
                    this.isGenerating = false;
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: error.response && error.response.data ? error.response.data : 'Lỗi kết nối lõi AI.', confirmButtonColor: '#dc3545' });
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
                            if (window.location.pathname === '/' || (window.location.pathname === '/profile' && this.profileTab === 'generated')) {
                                this.loadProfileGeneratedSongs();
                            }
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
            if (this.currentTrack.id === song.id && this.currentTrack.status === 'COMPLETED') {
                const audio = document.getElementById('audio-element');
                if (audio) {
                    if (audio.paused) {
                        audio.play().then(() => {
                            this.isPlaying = true;
                        }).catch(err => console.error(err));
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
                    audio.play().then(() => {
                        this.isPlaying = true;
                    }).catch(err => console.error(err));
                }
            });
        },

        incrementListenCount(song) {
            if (!song || !song.id) return;
            axios.post(`/api/songs/${song.id}/play`)
                .then(response => {
                    if (response.data && response.data.success) {
                        song.listenCount = response.data.listenCount;
                        if (this.currentTrack.id === song.id) {
                            this.currentTrack.listenCount = response.data.listenCount;
                        }
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
                    this.profileUsername = response.data.username;
                    this.loadFollowStatus();
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
            if (this.loginForm && !this.loginForm.username.trim()) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập tên đăng nhập!' });
                return;
            }
            if (this.loginForm && !this.loginForm.password.trim()) {
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
                    document.cookie = 'jwt_token=' + response.data.token + '; path=/; max-age=86400; SameSite=Lax';

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
                            window.location.href = '/admin';
                        } else {
                            window.location.href = '/';
                        }
                    }, 1000);
                })
                .catch(err => {
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-bolt"></i> Kích hoạt hệ thống';
                        btn.disabled = false;
                    }
                    let msg = 'Tài khoản hoặc mật khẩu không chính xác.';
                    if (err.response && err.response.status === 403) {
                        msg = err.response.data || 'Tài khoản đã bị khóa!';
                    } else if (err.response && err.response.data) {
                        msg = err.response.data.message || err.response.data || msg;
                    }
                    Swal.fire({
                        icon: 'error',
                        title: 'Đăng nhập thất bại',
                        text: msg,
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

            const btn = document.querySelector('button[type="submit"]');
            if (btn) { btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang khởi tạo...'; btn.disabled = true; }

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
                    if (btn) { btn.innerHTML = '<i class="ti ti-user-plus"></i> Khởi tạo tài khoản'; btn.disabled = false; }
                    Swal.fire({
                        icon: 'error',
                        title: 'Lỗi',
                        text: error.response && error.response.data ? "Đăng ký thất bại: " + (error.response.data.message || error.response.data) : "Tài khoản hoặc Email đã tồn tại."
                    });
                });
        },

        openForgotPasswordModal() {
            this.forgotPasswordForm = { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false };
            const modalElem = document.getElementById('forgotPasswordModal');
            if (modalElem) {
                const modal = new bootstrap.Modal(modalElem);
                modal.show();
            }
        },

        sendForgotPasswordOtp() {
            if (!this.forgotPasswordForm.email || !this.forgotPasswordForm.email.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng nhập Email của bạn!' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/forgot-password', { email: this.forgotPasswordForm.email.trim() })
                .then(res => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: res.data.message || 'Đã gửi mã OTP qua Email.' });
                    this.forgotPasswordForm.step = 2;
                })
                .catch(err => {
                    let msg = 'Không thể gửi mã OTP. Vui lòng thử lại sau.';
                    if (err.response && err.response.data) {
                        msg = err.response.data.message || err.response.data || msg;
                    }
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                })
                .finally(() => {
                    this.forgotPasswordForm.isSending = false;
                });
        },

        submitResetPassword() {
            if (!this.forgotPasswordForm.otp || !this.forgotPasswordForm.otp.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng nhập mã OTP 6 chữ số!' });
                return;
            }
            if (!this.forgotPasswordForm.newPassword || this.forgotPasswordForm.newPassword.length < 6) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Mật khẩu mới phải có ít nhất 6 ký tự!' });
                return;
            }
            if (this.forgotPasswordForm.newPassword !== this.forgotPasswordForm.confirmPassword) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Mật khẩu xác nhận không trùng khớp!' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/reset-password', {
                email: this.forgotPasswordForm.email.trim(),
                otp: this.forgotPasswordForm.otp.trim(),
                newPassword: this.forgotPasswordForm.newPassword
            })
                .then(res => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: res.data.message || 'Đặt lại mật khẩu thành công!' })
                        .then(() => {
                            const modalElem = document.getElementById('forgotPasswordModal');
                            if (modalElem) {
                                const modal = bootstrap.Modal.getInstance(modalElem);
                                if (modal) modal.hide();
                            }
                            if (this.loginForm) {
                                this.loginForm.password = '';
                            }
                        });
                })
                .catch(err => {
                    let msg = 'Đặt lại mật khẩu thất bại!';
                    if (err.response && err.response.data) {
                        msg = err.response.data.message || err.response.data || msg;
                    }
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                })
                .finally(() => {
                    this.forgotPasswordForm.isSending = false;
                });
        },

        handleLogout(showConfirm = true) {
            const executeLogout = () => {
                if (this.stompClient) {
                    try { this.stompClient.disconnect(); } catch(e) {}
                }
                localStorage.removeItem('music_username');
                localStorage.removeItem('jwt_token');
                localStorage.removeItem('music_is_admin');
                document.cookie = 'jwt_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax';
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
            this.profileModalError = '';
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

        closeProfileModal() {
            this.showProfileModal = false;
            this.profileModalError = '';
        },

        submitUpdateProfile() {
            this.profileModalError = '';
            if (!this.profileForm.fullname || !this.profileForm.fullname.trim()) {
                const msg = 'Họ và tên không được để trống!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }
            if (!this.profileForm.email || !this.profileForm.email.trim()) {
                const msg = 'Địa chỉ Email không được để trống!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }
            axios.put(`/api/users/${this.currentUser}/profile`, this.profileForm)
                .then(response => {
                    this.Toast.fire({ icon: 'success', title: 'Cập nhật hồ sơ thành công!' });
                    if (window.location.pathname === '/profile') this.loadProfilePageData();
                    this.showProfileModal = false;
                })
                .catch(error => {
                    let msg = 'Vui lòng kiểm tra lại thông tin.';
                    if (error.response && error.response.data) {
                        if (typeof error.response.data === 'string') {
                            msg = error.response.data;
                        } else if (error.response.data.message) {
                            msg = error.response.data.message;
                        } else if (typeof error.response.data === 'object') {
                            msg = Object.values(error.response.data).join(', ');
                        }
                    }
                    this.profileModalError = msg;
                    Swal.fire({ icon: 'error', title: 'Cập nhật thất bại', text: msg });
                });
        },

        submitChangePassword() {
            this.profileModalError = '';
            const oldPass = this.changePasswordForm.oldPassword ? this.changePasswordForm.oldPassword.trim() : '';
            const newPass = this.changePasswordForm.newPassword ? this.changePasswordForm.newPassword.trim() : '';
            const confirmPass = this.changePasswordForm.confirmNewPassword ? this.changePasswordForm.confirmNewPassword.trim() : '';

            if (!oldPass) {
                const msg = 'Mật khẩu hiện tại không được để trống!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }
            if (!newPass) {
                const msg = 'Mật khẩu mới không được để trống!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }
            if (newPass.length < 6) {
                const msg = 'Mật khẩu mới phải có ít nhất 6 ký tự!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }
            if (!confirmPass) {
                const msg = 'Xác nhận mật khẩu mới không được để trống!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }
            if (newPass !== confirmPass) {
                const msg = 'Mật khẩu mới và xác nhận mật khẩu không khớp!';
                this.profileModalError = msg;
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: msg });
                return;
            }

            const payload = {
                oldPassword: oldPass,
                newPassword: newPass,
                confirmNewPassword: confirmPass
            };

            axios.put(`/api/users/${this.currentUser}/change-password`, payload)
                .then(response => {
                    this.showProfileModal = false;
                    Swal.fire({ icon: 'success', title: 'Thành công!', text: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.', confirmButtonColor: '#16a34a' })
                        .then(() => { this.handleLogout(false); });
                })
                .catch(error => {
                    let msg = 'Đã có lỗi xảy ra.';
                    if (error.response && error.response.data) {
                        if (typeof error.response.data === 'string') {
                            msg = error.response.data;
                        } else if (error.response.data.message) {
                            msg = error.response.data.message;
                        }
                    }
                    this.profileModalError = msg;
                    Swal.fire({ icon: 'error', title: 'Đổi mật khẩu thất bại', text: msg });
                });
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

        formatRelativeTime(dateString) {
            if (!dateString) return '';
            const date = new Date(dateString);
            if (isNaN(date.getTime())) {
                return dateString.substring(0, 10);
            }
            const now = new Date();
            const seconds = Math.round((now - date) / 1000);
            const minutes = Math.round(seconds / 60);
            const hours = Math.round(minutes / 60);
            const days = Math.round(hours / 24);
            
            if (seconds < 0) {
                return 'Vừa xong';
            }
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
        goToOrders() { window.location.href = '/orders'; },

        // ================= METHODS CHO BOXCHAT =================
        connectWebSocket() {
            if (this.stompClient && this.stompClient.connected) return;
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;
            this.stompClient.connect({}, (frame) => {
                this.stompClient.subscribe('/user/queue/messages', (messageOutput) => {
                    const message = JSON.parse(messageOutput.body);
                    this.handleIncomingChatMessage(message);
                });
            }, (error) => {
                setTimeout(() => {
                    if (this.currentUser) this.connectWebSocket();
                }, 5000);
            });
        },

        isMyMessage(msg) {
            if (!msg || !msg.sender || !this.currentUser) return false;
            const senderName = typeof msg.sender === 'object' ? msg.sender.username : msg.sender;
            return senderName === this.currentUser;
        },

        handleIncomingChatMessage(rawMessage) {
            if (!rawMessage) return;
            const senderUsername = (typeof rawMessage.sender === 'object' && rawMessage.sender !== null) 
                ? rawMessage.sender.username 
                : rawMessage.sender;
            const recipientUsername = (typeof rawMessage.recipient === 'object' && rawMessage.recipient !== null) 
                ? rawMessage.recipient.username 
                : rawMessage.recipient;

            const normalizedMessage = {
                id: rawMessage.id,
                sender: senderUsername,
                recipient: recipientUsername,
                content: rawMessage.content,
                timestamp: rawMessage.timestamp,
                isRead: rawMessage.isRead
            };

            if (this.activeChatUser && 
                ((senderUsername === this.activeChatUser.username && recipientUsername === this.currentUser) ||
                 (senderUsername === this.currentUser && recipientUsername === this.activeChatUser.username))) {
                
                this.chatMessages.push(normalizedMessage);
                this.scrollToBottom();
                
                if (recipientUsername === this.currentUser) {
                    axios.put(`/api/chat/messages/read-all?partner=${senderUsername}`)
                        .then(() => { this.loadRecentChats(); });
                } else {
                    this.loadRecentChats();
                }
            } else {
                this.loadRecentChats();
                this.loadTotalUnreadCount();
                
                if (senderUsername !== this.currentUser) {
                    const senderDisplayName = (typeof rawMessage.sender === 'object' && rawMessage.sender !== null && rawMessage.sender.fullname) 
                        ? rawMessage.sender.fullname 
                        : senderUsername;
                    this.Toast.fire({
                        icon: 'info',
                        title: `Tin nhắn mới từ ${senderDisplayName}`,
                        text: (rawMessage.content || '').substring(0, 30) + ((rawMessage.content || '').length > 30 ? '...' : '')
                    });
                }
            }
        },

        loadRecentChats() {
            axios.get('/api/chat/recent-chats')
                .then(response => { this.chatContacts = response.data; })
                .catch(err => console.error("Lỗi tải tin nhắn gần đây:", err));
        },

        loadTotalUnreadCount() {
            axios.get('/api/chat/unread-count')
                .then(response => { this.totalUnreadCount = response.data.unreadCount; })
                .catch(err => console.error("Lỗi tải số tin nhắn chưa đọc:", err));
        },

        toggleChat() {
            this.chatOpen = !this.chatOpen;
            if (this.chatOpen) {
                this.loadRecentChats();
                this.loadTotalUnreadCount();
                if (this.activeChatUser) this.scrollToBottom();
            }
        },

        openChatRoom(contact) {
            this.activeChatUser = {
                username: contact.username,
                fullname: contact.fullname,
                photo: contact.photo
            };
            this.chatMessages = [];
            this.chatInput = '';
            
            axios.get(`/api/chat/history?partner=${contact.username}`)
                .then(response => {
                    this.chatMessages = response.data;
                    this.scrollToBottom();
                    return axios.put(`/api/chat/messages/read-all?partner=${contact.username}`);
                })
                .then(() => {
                    this.loadRecentChats();
                    this.loadTotalUnreadCount();
                })
                .catch(err => console.error("Lỗi tải lịch sử chat:", err));
        },

        backToContacts() {
            this.activeChatUser = null;
            this.chatMessages = [];
            this.chatInput = '';
            this.loadRecentChats();
            this.loadTotalUnreadCount();
        },

        searchChatUsers() {
            if (this.chatSearchTimeout) clearTimeout(this.chatSearchTimeout);
            if (!this.chatSearchQuery || !this.chatSearchQuery.trim()) {
                this.chatSearchResults = [];
                return;
            }
            this.chatSearchTimeout = setTimeout(() => {
                axios.get(`/api/chat/search-users?query=${this.chatSearchQuery}`)
                    .then(response => { this.chatSearchResults = response.data; })
                    .catch(err => console.error("Lỗi tìm kiếm user:", err));
            }, 300);
        },

        clearChatSearch() {
            this.chatSearchQuery = '';
            this.chatSearchResults = [];
        },

        startChatWith(username) {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Vui lòng đăng nhập để thực hiện nhắn tin với thành viên khác!',
                    confirmButtonColor: '#16a34a'
                });
                return;
            }
            axios.get(`/api/users/${username}/profile`)
                .then(response => {
                    const u = response.data;
                    const contact = {
                        username: u.username,
                        fullname: u.fullname,
                        photo: u.photo
                    };
                    this.chatOpen = true;
                    this.openChatRoom(contact);
                })
                .catch(err => {
                    Swal.fire('Lỗi', 'Không thể bắt đầu chat với người dùng này.', 'error');
                });
        },

        startChatWithUser(user) {
            this.clearChatSearch();
            this.openChatRoom(user);
        },

        sendChatMessage() {
            if (!this.chatInput || !this.chatInput.trim() || !this.activeChatUser || !this.stompClient || !this.stompClient.connected) return;
            const chatMessage = {
                recipient: { username: this.activeChatUser.username },
                content: this.chatInput.trim()
            };
            this.stompClient.send("/app/chat.send", {}, JSON.stringify(chatMessage));
            this.chatInput = '';
        },

        formatChatTime(dateString) {
            if (!dateString) return '';
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return '';
            const now = new Date();
            const isToday = date.toDateString() === now.toDateString();
            const pad = (n) => n < 10 ? '0' + n : n;
            const timeStr = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
            if (isToday) return timeStr;
            return `${pad(date.getDate())}/${pad(date.getMonth() + 1)} ${timeStr}`;
        },

        scrollToBottom() {
            setTimeout(() => {
                const container = document.getElementById('chat-body-scroll');
                if (container) container.scrollTop = container.scrollHeight;
            }, 100);
        }
    }
});