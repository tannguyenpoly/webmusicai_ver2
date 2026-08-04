// src/main/resources/static/js/app.js
import { utilsModule } from './modules/utils.js';
import { authModule } from './modules/auth.js';
import { userProfileModule } from './modules/userProfile.js';
import { songManagementModule } from './modules/songManagement.js';
import { exploreModule } from './modules/explore.js';
import { playlistModule } from './modules/playlist.js';
import { paymentModule } from './modules/payment.js';
import { chatModule } from './modules/chat.js';
import { globalDataModule } from './modules/globalData.js';

// Helper to merge data objects
function mergeData(...modules) {
    return function() {
        let merged = {};
        modules.forEach(module => {
            if (typeof module.data === 'function') {
                Object.assign(merged, module.data());
            } else if (module.data) {
                Object.assign(merged, module.data);
            }
        });
        return merged;
    };
}

// Helper to merge methods
function mergeMethods(...modules) {
    let merged = {};
    modules.forEach(module => {
        if (module.methods) {
            Object.assign(merged, module.methods);
        }
    });
    return merged;
}

// Helper to merge computed properties
function mergeComputed(...modules) {
    let merged = {};
    modules.forEach(module => {
        if (module.computed) {
            Object.assign(merged, module.computed);
        }
    });
    return merged;
}

new Vue({
    el: '#app', // Hoặc '#workspace-area' nếu đó là id chính của bạn
    data: mergeData(globalDataModule, authModule, userProfileModule, songManagementModule, exploreModule, playlistModule, paymentModule, chatModule),
    watch: {
        ...chatModule.watch,
        ...exploreModule.watch,
    },
    computed: {
        ...mergeComputed(globalDataModule, userProfileModule, exploreModule, songManagementModule),
    },
    created() {
        this.setupAxiosInterceptors(this);
    },
    mounted() {
        this.loadAvailableTags(); // from songManagementModule
        this.isOnSongDetailPage = window.location.pathname.startsWith('/song/'); // from songManagementModule
        if (this.isOnSongDetailPage) { // from songManagementModule
            const style = document.createElement('style');
            style.innerHTML = '.suno-sticky-player { display: none !important; }';
            document.head.appendChild(style);
        }
        this.Toast = this.setupToast(); // Call setupToast from utilsModule

        const urlParams = new URLSearchParams(window.location.search);
        const paymentStatus = urlParams.get('status');
        if (paymentStatus) { // from paymentModule
            if (paymentStatus === 'success') { // from paymentModule
                Swal.fire({ icon: 'success', title: 'Thanh toán thành công!', text: 'Hệ thống đã cập nhật token vào tài khoản của bạn.', confirmButtonColor: '#16a34a' }); // from paymentModule
            } else if (paymentStatus === 'failed') { // from paymentModule
                Swal.fire({ icon: 'error', title: 'Thanh toán thất bại!', text: 'Giao dịch chưa hoàn tất hoặc đã bị hủy.', confirmButtonColor: '#dc3545' }); // from paymentModule
            } else if (paymentStatus === 'invalid') { // from paymentModule
                Swal.fire({ icon: 'warning', title: 'Cảnh báo', text: 'Giao dịch không hợp lệ hoặc dữ liệu bị sai lệch.', confirmButtonColor: '#ffc107' }); // from paymentModule
            }
            window.history.replaceState(null, null, window.location.pathname); // from paymentModule
        }

        const userParam = urlParams.get('username');
        const isAdminParam = urlParams.get('isAdmin');
        const oauthStatus = urlParams.get('oauth');
        if (oauthStatus === 'success' && userParam) { // from authModule
            localStorage.setItem('music_username', userParam); // from authModule
            localStorage.setItem('music_is_admin', isAdminParam === 'true'); // from authModule
            const guestId = localStorage.getItem('music_guest_id'); // from authModule
            if (guestId) {
                this.migrateGuestSongs(guestId, userParam); // from userProfileModule
            }
            window.history.replaceState(null, null, window.location.pathname); // from authModule
            this.Toast.fire({ icon: 'success', title: `Chào mừng ${userParam} đã đăng nhập!` }); // from authModule
        }

        const savedUser = localStorage.getItem('music_username');

        if (savedUser) {
            this.currentUser = savedUser; // from userProfileModule
            this.isAdmin = localStorage.getItem('music_is_admin') === 'true'; // from userProfileModule
            this.generationForm.username = savedUser; // from songManagementModule
            this.loadUserTokenBalance(savedUser); // from userProfileModule

            setTimeout(() => {
                this.connectWebSocket(); // from chatModule
                this.loadRecentChats(); // from chatModule
                this.loadTotalUnreadCount(); // from chatModule
                this.startPresenceHeartbeat(); // from chatModule
                this.loadFriends(); // from chatModule
                this.loadNotifications(); // from chatModule
                this.notificationPollingTimer = setInterval(() => this.loadNotifications(false), 30000); // from chatModule
            }, 600);
        } else {
            this.currentUser = null; // from userProfileModule
            this.isAdmin = false; // from userProfileModule
            localStorage.removeItem('music_username'); // from authModule
            localStorage.removeItem('jwt_token'); // from authModule
            localStorage.removeItem('music_is_admin'); // from authModule

            // Setup Guest
            let guestId = localStorage.getItem('music_guest_id'); // from userProfileModule
            if (!guestId) { // from userProfileModule
                const randomHex = Math.random().toString(16).substring(2, 14); // from userProfileModule
                guestId = 'guest_' + randomHex; // from userProfileModule
                localStorage.setItem('music_guest_id', guestId); // from userProfileModule
            }
            this.isGuest = true; // from userProfileModule
            this.guestUsername = guestId; // from userProfileModule
            this.generationForm.username = guestId; // from songManagementModule
            this.userTokens = 5; // Default tokens for guest // from userProfileModule
            this.loadUserTokenBalance(guestId); // from userProfileModule
        }

        if (window.location.pathname === '/' || window.location.pathname === '/home') {
            this.loadPublicSongs(); // from exploreModule
        }
        else if (window.location.pathname === '/explore') {
            this.loadPublicSongs(); // from exploreModule
        }
        else if (window.location.pathname === '/create') {
            if (this.currentUser || this.isGuest) { // from userProfileModule
                this.loadGenres(); // from exploreModule
                this.profileUsername = this.currentUser || this.guestUsername; // from userProfileModule
                this.loadProfileGeneratedSongs(); // from userProfileModule
                if (this.currentUser) { // from userProfileModule
                    this.loadProfileFavorites(); // from userProfileModule
                }
                const promptParam = urlParams.get('prompt');
                if (promptParam) {
                    this.generationForm.prompt = promptParam; // from songManagementModule
                    const autoParam = urlParams.get('auto');
                    if (autoParam === 'true') {
                        setTimeout(() => {
                            this.generateMusic(); // from songManagementModule
                        }, 600);
                    }
                }
            }
        }
        else if (window.location.pathname.startsWith('/favorites')) {
            this.loadFavoriteSongs(); // from userProfileModule
        }
        else if (window.location.pathname.startsWith('/song/')) {
            const pathParts = window.location.pathname.split('/');
            const songId = pathParts[pathParts.length - 1];
            if (songId) {
                this.loadSingleSongAndComments(songId); // from songManagementModule
                this.loadPublicSongs(); // from exploreModule
            }
        }
        else if (window.location.pathname === '/orders') {
            this.loadPackages(); // from paymentModule
            if (this.currentUser) this.loadMyOrders(); // from paymentModule
        }
        else if (window.location.pathname === '/profile') {
            const urlParams = new URLSearchParams(window.location.search);
            const userParam = urlParams.get('username') || urlParams.get('u');
            this.profileUsername = userParam || this.currentUser; // from userProfileModule

            if (this.profileUsername) { // from userProfileModule
                this.loadProfilePageData(); // from userProfileModule
                this.loadProfileGeneratedSongs(); // from userProfileModule
                this.loadFriendStatus(); // from chatModule
                if (this.currentUser && this.profileUsername === this.currentUser) { // from userProfileModule
                    this.loadProfileFavorites(); // from userProfileModule
                } else { // from userProfileModule
                    this.profileTab = 'generated'; // from userProfileModule
                }
                this.loadFollowStatus(); // from chatModule
            }
        }

        this.loadSessionPlaylist(); // from playlistModule
    },
    methods: mergeMethods(utilsModule, authModule, userProfileModule, songManagementModule, exploreModule, playlistModule, paymentModule, chatModule, globalDataModule)
    }
);
