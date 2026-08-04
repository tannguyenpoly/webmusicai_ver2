// src/main/resources/static/js/modules/explore.js
export const exploreModule = {
    data() {
        return {
            publicSongs: [],
            filters: { keyword: '' },
            exploreSection: '',
            genres: [],
            matchingCreators: [],
            creatorSearchTimeout: null,
        };
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
                const getLikes = (song) => song.total_likes || 0;
                return getLikes(b) - getLikes(a);
            }).slice(0, 5);
        },
        activeExploreSongs() {
            if (this.exploreSection === 'trending') {
                return [...this.publicSongs].sort((a, b) =>
                    (b.total_likes || 0) - (a.total_likes || 0));
            }
            if (this.exploreSection === 'new') {
                return [...this.publicSongs].sort((a, b) =>
                    new Date(b.createdAt || b.created_at || 0)
                    - new Date(a.createdAt || a.created_at || 0));
            }
            return this.publicSongs;
        },
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
    methods: {
        loadPublicSongs() {
            axios.get('/api/songs/public')
                .then(response => { this.publicSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { console.error(error); });
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
            this.exploreSection = section;
            window.scrollTo({ top: 0, behavior: 'smooth' });
        },
        closeExploreSection() {
            this.exploreSection = '';
        },
        goToSongDetail(songId) {
            window.location.href = `/song/${songId}`;
        },
    }
};
