/**
 * ISP Billing Manager - Web Software Core Application Logic
 * Shared with Android Application Backend API
 */

const API_BASE = window.location.pathname.includes('/public/') ? '../api/' : './api/';

const AppState = {
    token: localStorage.getItem('isp_api_token') || '',
    user: JSON.parse(localStorage.getItem('isp_user') || 'null'),
    currentTab: 'dashboard',
    customers: [],
    packages: [],
    bills: [],
    payments: [],
    expenses: [],
    expenseCategories: [],
    settings: {
        isp_name: 'ISP Billing Management',
        hotline: '',
        address: '',
        currency_symbol: '৳',
        theme_mode: 'LIGHT'
    },
    activeMonth: new Date().toISOString().slice(0, 7), // YYYY-MM
    selectedCustomer: null,
    currentReceipt: null,
    receiptFormat: 'thermal', // 'thermal' or 'a4'
    isLoading: false
};

// ==========================================
// API CLIENT HELPER
// ==========================================
async function apiCall(endpoint, method = 'GET', body = null) {
    const headers = {
        'Accept': 'application/json'
    };

    if (AppState.token) {
        headers['Authorization'] = `Bearer ${AppState.token}`;
    }

    const config = { method, headers };

    if (body) {
        headers['Content-Type'] = 'application/json';
        config.body = JSON.stringify(body);
    }

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, config);
        const text = await response.text();

        let data;
        try {
            data = JSON.parse(text);
        } catch (e) {
            console.error('Non-JSON response from server:', text);
            throw new Error('Server returned invalid response format.');
        }

        if (response.status === 401) {
            handleUnauthorized();
            throw new Error(data.message || 'Session expired. Please log in again.');
        }

        return data;
    } catch (err) {
        console.error(`API Error [${endpoint}]:`, err);
        throw err;
    }
}

function handleUnauthorized() {
    AppState.token = '';
    AppState.user = null;
    localStorage.removeItem('isp_api_token');
    localStorage.removeItem('isp_user');
    showAuthView();
    showToast('Session expired. Please log in.', 'error');
}

// ==========================================
// TOAST NOTIFICATIONS
// ==========================================
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    const bgColors = {
        success: 'bg-emerald-600 text-white',
        error: 'bg-rose-600 text-white',
        warning: 'bg-amber-500 text-white',
        info: 'bg-blue-600 text-white'
    };

    const icons = {
        success: '✓',
        error: '✕',
        warning: '⚠',
        info: 'ℹ'
    };

    toast.className = `toast-msg px-4 py-3 rounded-lg shadow-lg flex items-center space-x-2 text-sm font-medium ${bgColors[type] || bgColors.info} animate-bounce`;
    toast.innerHTML = `<span class="font-bold text-base mr-1">${icons[type] || 'ℹ'}</span><span>${message}</span>`;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px)';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// ==========================================
// FORMATTING HELPERS
// ==========================================
function formatCurrency(amount) {
    const num = parseFloat(amount) || 0;
    const symbol = AppState.settings.currency_symbol || '৳';
    return `${symbol} ${num.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}

function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleDateString('bn-BD', { year: 'numeric', month: 'short', day: 'numeric' });
    } catch (_) {
        return dateStr;
    }
}

function normalizeMonth(rawMonth) {
    if (!rawMonth) return '';
    let str = String(rawMonth).trim();
    const bnDigits = {'০':'0','১':'1','২':'2','৩':'3','৪':'4','৫':'5','৬':'6','৭':'7','৮':'8','৯':'9'};
    str = str.replace(/[০-৯]/g, d => bnDigits[d] || d).toLowerCase();

    // Match YYYY-MM or YYYY/MM
    const ymMatch = str.match(/(\d{4})[-/.](\d{1,2})/);
    if (ymMatch) {
        return `${ymMatch[1]}-${ymMatch[2].padStart(2, '0')}`;
    }
    // Match MM-YYYY or MM/YYYY
    const myMatch = str.match(/(\d{1,2})[-/.](\d{4})/);
    if (myMatch) {
        return `${myMatch[2]}-${myMatch[1].padStart(2, '0')}`;
    }

    const months = {
        'january': '01', 'jan': '01', 'জানুয়ারি': '01', 'জানুয়ারী': '01',
        'february': '02', 'feb': '02', 'ফেব্রুয়ারি': '02', 'ফেব্রুয়ারী': '02',
        'march': '03', 'mar': '03', 'মার্চ': '03',
        'april': '04', 'apr': '04', 'এপ্রিল': '04',
        'may': '05', 'মে': '05',
        'june': '06', 'jun': '06', 'জুন': '06',
        'july': '07', 'jul': '07', 'জুলাই': '07',
        'august': '08', 'aug': '08', 'আগস্ট': '08', 'আগষ্ট': '08',
        'september': '09', 'sep': '09', 'sept': '09', 'সেপ্টেম্বর': '09',
        'october': '10', 'oct': '10', 'অক্টোবর': '10',
        'november': '11', 'nov': '11', 'নভেম্বর': '11',
        'december': '12', 'dec': '12', 'ডিসেম্বর': '12'
    };

    const yearMatch = str.match(/\b(20\d{2}|19\d{2})\b/);
    const year = yearMatch ? yearMatch[1] : '';
    let foundMonth = '';
    for (const [name, num] of Object.entries(months)) {
        if (str.includes(name)) {
            foundMonth = num;
            break;
        }
    }
    if (year && foundMonth) {
        return `${year}-${foundMonth}`;
    }
    return str;
}

function isSameMonth(mA, mB) {
    if (!mA || !mB) return false;
    const nA = normalizeMonth(mA);
    const nB = normalizeMonth(mB);
    return Boolean(nA && nA === nB);
}

function formatMonthDisplay(rawMonth) {
    if (!rawMonth) return '';
    const ym = normalizeMonth(rawMonth);
    if (ym && ym.includes('-')) {
        const [year, month] = ym.split('-');
        const date = new Date(parseInt(year), parseInt(month) - 1, 1);
        return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
    }
    return rawMonth;
}

function deduplicateBills(bills) {
    if (!Array.isArray(bills)) return [];
    const map = new Map();
    for (const b of bills) {
        const normM = normalizeMonth(b.month || b.bill_month);
        const key = `${b.customer_id}_${normM}`;
        if (!map.has(key)) {
            map.set(key, b);
        } else {
            const existing = map.get(key);
            const existingIsPaid = (existing.status || '').toUpperCase() === 'PAID';
            const currentIsPaid = (b.status || '').toUpperCase() === 'PAID';
            if (!existingIsPaid && currentIsPaid) {
                map.set(key, b);
            } else if (existingIsPaid === currentIsPaid && Number(b.id) > Number(existing.id)) {
                map.set(key, b);
            }
        }
    }
    return Array.from(map.values());
}

// ==========================================
// AUTHENTICATION LOGIC
// ==========================================
async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;

    if (!email || !password) {
        showToast('Please enter both email and password.', 'error');
        return;
    }

    const btn = document.getElementById('login-submit-btn');
    btn.disabled = true;
    btn.innerText = 'Logging in...';

    try {
        const res = await apiCall('login.php', 'POST', { email, password });
        if (res.status && res.user && res.user.api_token) {
            AppState.token = res.user.api_token;
            AppState.user = res.user;
            localStorage.setItem('isp_api_token', res.user.api_token);
            localStorage.setItem('isp_user', JSON.stringify(res.user));

            showToast('Login successful!', 'success');
            showAppView();
            await loadInitialData();
        } else {
            showToast(res.message || 'Login failed. Please check credentials.', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Login error occurred.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Login';
    }
}

async function handleSignup(e) {
    e.preventDefault();
    const name = document.getElementById('signup-name').value.trim();
    const email = document.getElementById('signup-email').value.trim();
    const phone = document.getElementById('signup-phone').value.trim();
    const password = document.getElementById('signup-password').value;

    if (!name || !email || !password) {
        showToast('Please fill all required fields.', 'error');
        return;
    }

    if (password.length < 6) {
        showToast('Password must be at least 6 characters.', 'error');
        return;
    }

    const btn = document.getElementById('signup-submit-btn');
    btn.disabled = true;
    btn.innerText = 'Creating Account...';

    try {
        const res = await apiCall('signup.php', 'POST', { name, email, phone, password });
        if (res.status && res.user) {
            AppState.token = res.user.api_token;
            AppState.user = res.user;
            localStorage.setItem('isp_api_token', res.user.api_token);
            localStorage.setItem('isp_user', JSON.stringify(res.user));

            showToast('Account created successfully!', 'success');
            showAppView();
            await loadInitialData();
        } else {
            showToast(res.message || 'Signup failed.', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Signup error.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Sign Up';
    }
}

function handleLogout() {
    if (!confirm('Are you sure you want to log out?')) return;
    stopAutoSync();
    AppState.token = '';
    AppState.user = null;
    localStorage.removeItem('isp_api_token');
    localStorage.removeItem('isp_user');
    showAuthView();
    showToast('Logged out successfully.', 'info');
}

// ==========================================
// VIEW SWITCHING (AUTH VS MAIN APP)
// ==========================================
function showAuthView() {
    document.getElementById('auth-container').classList.remove('hidden');
    document.getElementById('app-container').classList.add('hidden');
    switchAuthTab('login');
}

function showAppView() {
    document.getElementById('auth-container').classList.add('hidden');
    document.getElementById('app-container').classList.remove('hidden');
    if (AppState.user) {
        document.getElementById('header-user-name').innerText = AppState.user.name || 'Admin';
        document.getElementById('header-user-email').innerText = AppState.user.email || '';
    }
    switchTab('dashboard');
}

function switchAuthTab(tab) {
    const loginCard = document.getElementById('login-card');
    const signupCard = document.getElementById('signup-card');
    const forgotCard = document.getElementById('forgot-card');

    loginCard.classList.add('hidden');
    signupCard.classList.add('hidden');
    forgotCard.classList.add('hidden');

    if (tab === 'signup') {
        signupCard.classList.remove('hidden');
    } else if (tab === 'forgot') {
        forgotCard.classList.remove('hidden');
    } else {
        loginCard.classList.remove('hidden');
    }
}

function toggleMobileDrawer(open) {
    const drawer = document.getElementById('mobile-drawer');
    const backdrop = document.getElementById('mobile-drawer-backdrop');
    if (!drawer) return;

    if (open) {
        drawer.classList.remove('-translate-x-full');
        drawer.classList.add('translate-x-0');
        if (backdrop) backdrop.classList.remove('hidden');
    } else {
        drawer.classList.add('-translate-x-full');
        drawer.classList.remove('translate-x-0');
        if (backdrop) backdrop.classList.add('hidden');
    }
}

function switchTab(tabId) {
    AppState.currentTab = tabId;

    // Close mobile drawer if open
    toggleMobileDrawer(false);

    // Update top bar title on mobile
    const titleMap = {
        'dashboard': AppState.settings.isp_name || 'ড্যাশবোর্ড',
        'customers': 'গ্রাহক তালিকা',
        'billing': 'মাসিক বিল ও বকেয়া',
        'payments': 'বিল আদায় ও রিসিপ্ট',
        'packages': 'ইন্টারনেট প্যাকেজ',
        'expenses': 'খরচ হিসাব',
        'logs': 'অ্যাক্টিভিটি লগ',
        'settings': 'সিস্টেম সেটিংস'
    };
    const topTitle = document.getElementById('top-bar-title');
    if (topTitle) {
        topTitle.innerText = titleMap[tabId] || AppState.settings.isp_name || 'ISP Billing';
    }

    // Update sidebar navigation active classes
    document.querySelectorAll('.nav-link').forEach(link => {
        if (link.dataset.tab === tabId) {
            link.classList.add('bg-blue-50', 'text-blue-600', 'font-semibold', 'dark:bg-slate-800', 'dark:text-blue-400');
            link.classList.remove('text-gray-600', 'hover:bg-gray-50', 'dark:text-gray-300');
        } else {
            link.classList.remove('bg-blue-50', 'text-blue-600', 'font-semibold', 'dark:bg-slate-800', 'dark:text-blue-400');
            link.classList.add('text-gray-600', 'hover:bg-gray-50', 'dark:text-gray-300');
        }
    });

    // Update mobile bottom nav active classes
    document.querySelectorAll('.bottom-nav-btn').forEach(btn => {
        if (btn.dataset.tab === tabId) {
            btn.classList.add('text-blue-600', 'dark:text-blue-400');
            btn.classList.remove('text-gray-500', 'dark:text-gray-400');
            btn.querySelector('span')?.classList.add('font-semibold');
        } else {
            btn.classList.remove('text-blue-600', 'dark:text-blue-400');
            btn.classList.add('text-gray-500', 'dark:text-gray-400');
            btn.querySelector('span')?.classList.remove('font-semibold');
        }
    });

    // Toggle tab panes
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.classList.remove('active');
    });

    const activePane = document.getElementById(`tab-${tabId}`);
    if (activePane) {
        activePane.classList.add('active');
    }

    // Refresh specific tab data
    renderCurrentTab();
}

function renderCurrentTab() {
    const tabId = AppState.currentTab || 'dashboard';
    if (tabId === 'dashboard') renderDashboard();
    else if (tabId === 'customers') renderCustomers();
    else if (tabId === 'billing') renderBilling();
    else if (tabId === 'payments') renderPayments();
    else if (tabId === 'packages') renderPackages();
    else if (tabId === 'expenses') renderExpenses();
    else if (tabId === 'settings') renderSettings();
    else if (tabId === 'logs') loadAuditLogs();
}

// ==========================================
// BACKGROUND LIVE AUTO-SYNC ENGINE
// ==========================================
let autoSyncInterval = null;
let isSilentSyncActive = false;

async function triggerSilentSync() {
    if (!AppState.token || isSilentSyncActive) return;

    // Guard: Do not re-render if user is interacting with an input or modal is open
    const activeModals = document.querySelectorAll('.modal:not(.hidden), [id$="-modal"]:not(.hidden)');
    const isTyping = document.activeElement && ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName);
    if (activeModals.length > 0 || isTyping) {
        return;
    }

    isSilentSyncActive = true;
    const badgeText = document.getElementById('live-sync-text');
    const badgeDot = document.getElementById('live-sync-dot');

    if (badgeText) badgeText.innerText = 'Syncing...';
    if (badgeDot) {
        badgeDot.className = 'w-2 h-2 mr-1.5 bg-blue-500 rounded-full animate-ping';
    }

    try {
        await Promise.all([
            fetchCustomers(),
            fetchBills(),
            fetchPayments(),
            fetchExpenses()
        ]);

        renderCurrentTab();

        if (badgeText) badgeText.innerText = 'Auto-Sync: Live';
        if (badgeDot) {
            badgeDot.className = 'w-2 h-2 mr-1.5 bg-emerald-500 rounded-full animate-ping';
        }
    } catch (e) {
        console.warn('Silent auto-sync notice:', e.message);
        if (badgeText) badgeText.innerText = 'Offline';
        if (badgeDot) {
            badgeDot.className = 'w-2 h-2 mr-1.5 bg-rose-500 rounded-full';
        }
    } finally {
        isSilentSyncActive = false;
    }
}

function startAutoSync() {
    if (autoSyncInterval) clearInterval(autoSyncInterval);
    // Automatically poll every 20 seconds for seamless 2-way cloud synchronization
    autoSyncInterval = setInterval(triggerSilentSync, 20000);

    // Sync immediately when window/tab regains focus
    window.removeEventListener('focus', triggerSilentSync);
    window.addEventListener('focus', triggerSilentSync);

    // Sync immediately when internet re-connects
    window.removeEventListener('online', triggerSilentSync);
    window.addEventListener('online', triggerSilentSync);
}

function stopAutoSync() {
    if (autoSyncInterval) {
        clearInterval(autoSyncInterval);
        autoSyncInterval = null;
    }
}

// ==========================================
// DATA LOADING
// ==========================================
async function loadInitialData() {
    setLoading(true);
    try {
        await Promise.all([
            fetchSettings(),
            fetchPackages(),
            fetchCustomers(),
            fetchBills(),
            fetchPayments(),
            fetchExpenses()
        ]);
        renderDashboard();
        startAutoSync();
    } catch (e) {
        console.error('Error loading initial data:', e);
        showToast('Failed to load some data. Please check connection.', 'warning');
    } finally {
        setLoading(false);
    }
}

function setLoading(isLoading) {
    AppState.isLoading = isLoading;
    const loader = document.getElementById('global-loader');
    if (loader) {
        if (isLoading) loader.classList.remove('hidden');
        else loader.classList.add('hidden');
    }
}

async function fetchSettings() {
    try {
        const res = await apiCall('settings.php');
        if (res.status && res.data) {
            AppState.settings = Object.assign(AppState.settings, res.data);
            document.getElementById('brand-title').innerText = AppState.settings.isp_name || 'ISP Billing Manager';
            applyTheme(AppState.settings.theme_mode);
        }
    } catch (e) {
        console.warn('Settings load error:', e);
    }
}

async function fetchPackages() {
    const res = await apiCall('packages.php');
    if (res.status && Array.isArray(res.data)) {
        AppState.packages = res.data;
    }
}

async function fetchCustomers() {
    const res = await apiCall('customers.php');
    if (res.status && Array.isArray(res.data)) {
        AppState.customers = res.data;
    }
}

async function fetchBills() {
    const res = await apiCall('bills.php');
    if (res.status && Array.isArray(res.data)) {
        AppState.bills = deduplicateBills(res.data);
    }
}

async function fetchPayments() {
    const res = await apiCall('payments.php');
    if (res.status && Array.isArray(res.data)) {
        AppState.payments = res.data;
    }
}

async function fetchExpenses() {
    const res = await apiCall('expenses.php');
    if (res.status && Array.isArray(res.data)) {
        AppState.expenses = res.data;
    }
    const catRes = await apiCall('expense_categories.php');
    if (catRes.status && Array.isArray(catRes.data)) {
        AppState.expenseCategories = catRes.data;
    }
}

// ==========================================
// DASHBOARD RENDERING
// ==========================================
function renderDashboard() {
    const totalCustomers = AppState.customers.length;
    const activeCustomers = AppState.customers.filter(c => (c.status || '').toUpperCase() === 'ACTIVE').length;
    const inactiveCustomers = totalCustomers - activeCustomers;

    // Filter current month bills
    const currentMonth = AppState.activeMonth;
    const monthBills = AppState.bills.filter(b => isSameMonth(b.month || b.bill_month, currentMonth));

    const totalBilled = monthBills.reduce((sum, b) => sum + (parseFloat(b.amount) || 0), 0);
    const totalCollected = monthBills.reduce((sum, b) => sum + (parseFloat(b.paid_amount) || 0), 0);
    const totalDue = monthBills.reduce((sum, b) => sum + (parseFloat(b.due_amount) || 0), 0);

    // Current month expenses
    const monthExpenses = AppState.expenses.filter(e => (e.date && e.date.startsWith(currentMonth)));
    const totalExpenseAmount = monthExpenses.reduce((sum, e) => sum + (parseFloat(e.amount) || 0), 0);
    const netProfit = totalCollected - totalExpenseAmount;

    document.getElementById('stat-total-customers').innerText = totalCustomers;
    document.getElementById('stat-active-customers').innerText = activeCustomers;
    document.getElementById('stat-inactive-customers').innerText = inactiveCustomers;

    document.getElementById('stat-total-billed').innerText = formatCurrency(totalBilled);
    document.getElementById('stat-total-collected').innerText = formatCurrency(totalCollected);
    document.getElementById('stat-total-due').innerText = formatCurrency(totalDue);
    document.getElementById('stat-total-expenses').innerText = formatCurrency(totalExpenseAmount);
    document.getElementById('stat-net-profit').innerText = formatCurrency(netProfit);

    document.getElementById('dash-active-month-badge').innerText = formatMonthDisplay(currentMonth);

    // Recent Payments table
    const recentPaymentsTbody = document.getElementById('recent-payments-tbody');
    const recent = [...AppState.payments].slice(0, 5);

    if (recent.length === 0) {
        recentPaymentsTbody.innerHTML = `<tr><td colspan="5" class="py-4 text-center text-gray-400">No recent payments recorded.</td></tr>`;
    } else {
        recentPaymentsTbody.innerHTML = recent.map(p => `
            <tr class="border-b dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-slate-800">
                <td class="py-3 px-4 font-mono text-xs text-blue-600 font-semibold">${p.payment_receipt_no || p.id}</td>
                <td class="py-3 px-4 font-medium text-gray-800 dark:text-gray-200">${p.customer_name || 'N/A'}</td>
                <td class="py-3 px-4 font-bold text-emerald-600">${formatCurrency(p.amount)}</td>
                <td class="py-3 px-4 text-xs text-gray-500">${p.payment_date || ''} (${p.payment_method || 'Cash'})</td>
                <td class="py-3 px-4 text-right">
                    <button onclick="printReceiptDirect(${p.id})" class="text-xs px-2 py-1 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded dark:bg-slate-700 dark:text-gray-300">
                        Print Receipt
                    </button>
                </td>
            </tr>
        `).join('');
    }
}

// ==========================================
// CUSTOMER MANAGEMENT
// ==========================================
function renderCustomers() {
    const tbody = document.getElementById('customers-tbody');
    const searchVal = (document.getElementById('customer-search-input')?.value || '').toLowerCase().trim();
    const statusVal = document.getElementById('customer-filter-status')?.value || 'ALL';
    const pkgVal = document.getElementById('customer-filter-pkg')?.value || 'ALL';

    // Populate package filter dropdown if not populated
    const pkgFilter = document.getElementById('customer-filter-pkg');
    if (pkgFilter && pkgFilter.children.length <= 1) {
        AppState.packages.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.id;
            opt.innerText = `${p.name} (${formatCurrency(p.price)})`;
            pkgFilter.appendChild(opt);
        });
    }

    let filtered = AppState.customers.filter(c => {
        const matchesSearch = !searchVal ||
            (c.name && c.name.toLowerCase().includes(searchVal)) ||
            (c.phone && c.phone.toLowerCase().includes(searchVal)) ||
            (c.customer_code && c.customer_code.toLowerCase().includes(searchVal)) ||
            (c.ip_address && c.ip_address.toLowerCase().includes(searchVal)) ||
            (c.pppoe_username && c.pppoe_username.toLowerCase().includes(searchVal));

        const matchesStatus = statusVal === 'ALL' || (c.status || 'ACTIVE').toUpperCase() === statusVal;
        const matchesPkg = pkgVal === 'ALL' || String(c.package_id) === String(pkgVal);

        return matchesSearch && matchesStatus && matchesPkg;
    });

    document.getElementById('customer-count-badge').innerText = `${filtered.length} of ${AppState.customers.length}`;

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="py-8 text-center text-gray-400">No customers found matching filter.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(c => {
        const pkg = AppState.packages.find(p => String(p.id) === String(c.package_id));
        const pkgName = pkg ? `${pkg.name} (${formatCurrency(pkg.price)})` : (c.package_id || 'None');
        const isActive = (c.status || 'ACTIVE').toUpperCase() === 'ACTIVE';

        return `
            <tr class="border-b dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-slate-800 transition">
                <td class="py-3 px-4 font-mono text-xs font-semibold text-gray-500">${c.customer_code || c.id}</td>
                <td class="py-3 px-4 font-medium text-gray-900 dark:text-gray-100">
                    <div>${c.name}</div>
                    <div class="text-xs text-gray-400">${c.phone || 'No phone'}</div>
                </td>
                <td class="py-3 px-4 text-sm text-gray-600 dark:text-gray-300">
                    <div>${pkgName}</div>
                    <div class="text-xs text-blue-500 font-mono">${c.pppoe_username || c.ip_address || ''}</div>
                </td>
                <td class="py-3 px-4 text-xs text-gray-500">Day ${c.billing_cycle_date || 1}</td>
                <td class="py-3 px-4">
                    <span class="px-2 py-1 text-xs font-semibold rounded-full ${isActive ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200' : 'bg-rose-100 text-rose-800 dark:bg-rose-900 dark:text-rose-200'}">
                        ${c.status || 'ACTIVE'}
                    </span>
                </td>
                <td class="py-3 px-4 text-right space-x-1 whitespace-nowrap">
                    <button onclick="openCustomerDetails('${c.id}')" class="px-2 py-1 text-xs bg-blue-50 text-blue-600 hover:bg-blue-100 rounded dark:bg-slate-700 dark:text-blue-300">View</button>
                    <button onclick="openCollectBillForCustomer('${c.id}')" class="px-2 py-1 text-xs bg-emerald-600 text-white hover:bg-emerald-700 rounded">Collect Bill</button>
                    <button onclick="openEditCustomerModal('${c.id}')" class="px-2 py-1 text-xs bg-gray-100 text-gray-700 hover:bg-gray-200 rounded dark:bg-slate-700 dark:text-gray-200">Edit</button>
                    <button onclick="deleteCustomer('${c.id}')" class="px-2 py-1 text-xs bg-rose-50 text-rose-600 hover:bg-rose-100 rounded dark:bg-slate-700 dark:text-rose-400">Del</button>
                </td>
            </tr>
        `;
    }).join('');
}

function openAddCustomerModal() {
    document.getElementById('customer-modal-title').innerText = 'Add New Customer';
    document.getElementById('customer-form').reset();
    document.getElementById('cust-id').value = '';

    // Populate packages in select
    const pkgSelect = document.getElementById('cust-package-id');
    pkgSelect.innerHTML = AppState.packages.map(p => `
        <option value="${p.id}">${p.name} - ${formatCurrency(p.price)} (${p.speed || 'Standard'})</option>
    `).join('');

    document.getElementById('customer-modal').classList.remove('hidden');
}

function openEditCustomerModal(id) {
    const customer = AppState.customers.find(c => String(c.id) === String(id));
    if (!customer) return;

    document.getElementById('customer-modal-title').innerText = 'Edit Customer';
    document.getElementById('cust-id').value = customer.id;
    document.getElementById('cust-name').value = customer.name || '';
    document.getElementById('cust-phone').value = customer.phone || '';
    document.getElementById('cust-address').value = customer.address || '';
    document.getElementById('cust-code').value = customer.customer_code || '';
    document.getElementById('cust-ip').value = customer.ip_address || '';
    document.getElementById('cust-pppoe').value = customer.pppoe_username || '';
    document.getElementById('cust-cycle-date').value = customer.billing_cycle_date || 1;
    document.getElementById('cust-status').value = (customer.status || 'ACTIVE').toUpperCase();

    const pkgSelect = document.getElementById('cust-package-id');
    pkgSelect.innerHTML = AppState.packages.map(p => `
        <option value="${p.id}" ${String(p.id) === String(customer.package_id) ? 'selected' : ''}>${p.name} - ${formatCurrency(p.price)}</option>
    `).join('');

    document.getElementById('customer-modal').classList.remove('hidden');
}

async function handleCustomerSave(e) {
    e.preventDefault();
    const id = document.getElementById('cust-id').value || `cust_${Date.now()}`;
    const name = document.getElementById('cust-name').value.trim();
    const phone = document.getElementById('cust-phone').value.trim();
    const address = document.getElementById('cust-address').value.trim();
    const customer_code = document.getElementById('cust-code').value.trim();
    const ip_address = document.getElementById('cust-ip').value.trim();
    const pppoe_username = document.getElementById('cust-pppoe').value.trim();
    const package_id = document.getElementById('cust-package-id').value;
    const billing_cycle_date = parseInt(document.getElementById('cust-cycle-date').value) || 1;
    const status = document.getElementById('cust-status').value;

    const payload = {
        id,
        name,
        phone,
        address,
        customer_code,
        ip_address,
        pppoe_username,
        package_id,
        billing_cycle_date,
        status,
        joining_date: new Date().toISOString().slice(0, 10)
    };

    setLoading(true);
    try {
        const res = await apiCall('customers.php', 'POST', payload);
        if (res.status) {
            showToast('Customer saved successfully!', 'success');
            document.getElementById('customer-modal').classList.add('hidden');
            await fetchCustomers();
            renderCustomers();
            renderDashboard();
        } else {
            showToast(res.message || 'Error saving customer.', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Failed to save customer.', 'error');
    } finally {
        setLoading(false);
    }
}

async function deleteCustomer(id) {
    if (!confirm('Are you sure you want to delete this customer? This action cannot be undone.')) return;
    setLoading(true);
    try {
        const res = await apiCall(`customers.php?id=${encodeURIComponent(id)}`, 'DELETE');
        if (res.status) {
            showToast('Customer deleted successfully.', 'info');
            await fetchCustomers();
            renderCustomers();
            renderDashboard();
        } else {
            showToast(res.message || 'Error deleting customer.', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Failed to delete customer.', 'error');
    } finally {
        setLoading(false);
    }
}

function openCustomerDetails(id) {
    const cust = AppState.customers.find(c => String(c.id) === String(id));
    if (!cust) return;

    AppState.selectedCustomer = cust;
    const pkg = AppState.packages.find(p => String(p.id) === String(cust.package_id));

    document.getElementById('detail-cust-name').innerText = cust.name;
    document.getElementById('detail-cust-code').innerText = cust.customer_code || cust.id;
    document.getElementById('detail-cust-phone').innerText = cust.phone || 'N/A';
    document.getElementById('detail-cust-address').innerText = cust.address || 'N/A';
    document.getElementById('detail-cust-ip').innerText = cust.ip_address || 'N/A';
    document.getElementById('detail-cust-pppoe').innerText = cust.pppoe_username || 'N/A';
    document.getElementById('detail-cust-package').innerText = pkg ? `${pkg.name} (${formatCurrency(pkg.price)})` : 'None';
    document.getElementById('detail-cust-status').innerText = cust.status || 'ACTIVE';

    // Customer bills
    const custBills = AppState.bills.filter(b => String(b.customer_id) === String(cust.id));
    const billsTbody = document.getElementById('detail-cust-bills-tbody');
    billsTbody.innerHTML = custBills.length === 0 ? `<tr><td colspan="4" class="py-3 text-center text-gray-400">No bills on record.</td></tr>` : custBills.map(b => `
        <tr class="border-b dark:border-gray-700">
            <td class="py-2 px-3 font-medium">${b.month || b.bill_month}</td>
            <td class="py-2 px-3">${formatCurrency(b.amount)}</td>
            <td class="py-2 px-3 text-emerald-600 font-bold">${formatCurrency(b.paid_amount)}</td>
            <td class="py-2 px-3"><span class="px-2 py-0.5 text-xs rounded ${b.status === 'PAID' ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}">${b.status}</span></td>
        </tr>
    `).join('');

    // Customer payments
    const custPayments = AppState.payments.filter(p => String(p.customer_id) === String(cust.id));
    const paymentsTbody = document.getElementById('detail-cust-payments-tbody');
    paymentsTbody.innerHTML = custPayments.length === 0 ? `<tr><td colspan="4" class="py-3 text-center text-gray-400">No payments made.</td></tr>` : custPayments.map(p => `
        <tr class="border-b dark:border-gray-700">
            <td class="py-2 px-3 font-mono text-xs">${p.payment_receipt_no || p.id}</td>
            <td class="py-2 px-3 text-emerald-600 font-bold">${formatCurrency(p.amount)}</td>
            <td class="py-2 px-3 text-xs text-gray-500">${p.payment_date} (${p.payment_method})</td>
            <td class="py-2 px-3 text-right"><button onclick="printReceiptDirect(${p.id})" class="text-xs text-blue-600 underline">Receipt</button></td>
        </tr>
    `).join('');

    document.getElementById('customer-detail-modal').classList.remove('hidden');
}

// ==========================================
// BILLING MANAGEMENT
// ==========================================
function renderBilling() {
    const tbody = document.getElementById('billing-tbody');
    const selectedMonth = document.getElementById('billing-month-filter')?.value || AppState.activeMonth;
    const statusFilter = document.getElementById('billing-status-filter')?.value || 'ALL';

    const billsForMonth = AppState.bills.filter(b => {
        const matchesMonth = isSameMonth(b.month || b.bill_month, selectedMonth);
        const matchesStatus = statusFilter === 'ALL' || (b.status || 'UNPAID').toUpperCase() === statusFilter;
        return matchesMonth && matchesStatus;
    });

    document.getElementById('billing-count-badge').innerText = `${billsForMonth.length} bills`;

    if (billsForMonth.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="py-8 text-center text-gray-400">No bills generated for ${formatMonthDisplay(selectedMonth)}.</td></tr>`;
        return;
    }

    tbody.innerHTML = billsForMonth.map(b => {
        const isPaid = (b.status || 'UNPAID').toUpperCase() === 'PAID';
        return `
            <tr class="border-b dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-slate-800">
                <td class="py-3 px-4 font-mono text-xs font-semibold text-gray-500">${b.bill_number || b.id}</td>
                <td class="py-3 px-4 font-medium text-gray-900 dark:text-gray-100">${b.customer_name || 'Customer #' + b.customer_id}</td>
                <td class="py-3 px-4 font-bold text-gray-800 dark:text-gray-200">${formatCurrency(b.amount)}</td>
                <td class="py-3 px-4 text-emerald-600 font-semibold">${formatCurrency(b.paid_amount)}</td>
                <td class="py-3 px-4 text-rose-600 font-bold">${formatCurrency(b.due_amount)}</td>
                <td class="py-3 px-4">
                    <span class="px-2 py-1 text-xs font-semibold rounded-full ${isPaid ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200' : 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200'}">
                        ${b.status || 'UNPAID'}
                    </span>
                </td>
                <td class="py-3 px-4 text-right space-x-1">
                    ${!isPaid ? `<button onclick="openCollectBillForBill(${b.id})" class="px-2 py-1 text-xs bg-emerald-600 hover:bg-emerald-700 text-white rounded">Collect</button>` : ''}
                    <button onclick="deleteBill(${b.id})" class="px-2 py-1 text-xs bg-rose-50 text-rose-600 hover:bg-rose-100 rounded">Delete</button>
                </td>
            </tr>
        `;
    }).join('');
}

async function generateMonthlyBillsBulk() {
    const month = document.getElementById('billing-month-filter')?.value || AppState.activeMonth;
    if (!confirm(`Generate monthly bills for all active customers for ${formatMonthDisplay(month)}?`)) return;

    setLoading(true);
    let generatedCount = 0;

    const activeCustomers = AppState.customers.filter(c => (c.status || 'ACTIVE').toUpperCase() === 'ACTIVE');

    for (const cust of activeCustomers) {
        // Check if bill already exists for this month across all format representations
        const exists = AppState.bills.some(b => String(b.customer_id) === String(cust.id) && isSameMonth(b.month || b.bill_month, month));
        if (exists) continue;

        const pkg = AppState.packages.find(p => String(p.id) === String(cust.package_id));
        const amount = pkg ? parseFloat(pkg.price) : 500;
        const normMonth = normalizeMonth(month);

        const billPayload = {
            id: Date.now() + Math.floor(Math.random() * 10000),
            customer_id: cust.id,
            customer_name: cust.name,
            customer_code: cust.customer_code,
            month: normMonth,
            bill_month: normMonth,
            amount: amount,
            paid_amount: 0.00,
            due_amount: amount,
            status: 'UNPAID',
            due_date: `${normMonth}-10`,
            generated_date: new Date().toISOString().slice(0, 10)
        };

        try {
            await apiCall('bills.php', 'POST', billPayload);
            generatedCount++;
        } catch (e) {
            console.error('Error generating bill for customer:', cust.id, e);
        }
    }

    setLoading(false);
    showToast(`Generated ${generatedCount} bills successfully!`, 'success');
    await fetchBills();
    renderBilling();
    renderDashboard();
}

async function deleteBill(billId) {
    if (!confirm('Are you sure you want to delete this bill?')) return;
    setLoading(true);
    try {
        const res = await apiCall(`bills.php?id=${billId}`, 'DELETE');
        if (res.status) {
            showToast('Bill deleted.', 'info');
            await fetchBills();
            renderBilling();
            renderDashboard();
        } else {
            showToast(res.message || 'Error deleting bill', 'error');
        }
    } catch (e) {
        showToast(e.message || 'Failed to delete bill', 'error');
    } finally {
        setLoading(false);
    }
}

// ==========================================
// PAYMENT COLLECTION & RECEIPT
// ==========================================
function openCollectBillForCustomer(customerId) {
    const cust = AppState.customers.find(c => String(c.id) === String(customerId));
    if (!cust) return;

    // Find latest unpaid bill
    const unpaidBill = AppState.bills.find(b => String(b.customer_id) === String(customerId) && b.status !== 'PAID');

    document.getElementById('pay-customer-id').value = cust.id;
    document.getElementById('pay-customer-name-display').innerText = `${cust.name} (${cust.phone || 'No phone'})`;
    document.getElementById('pay-bill-id').value = unpaidBill ? unpaidBill.id : 0;
    document.getElementById('pay-amount').value = unpaidBill ? unpaidBill.due_amount : 500;
    document.getElementById('pay-date').value = new Date().toISOString().slice(0, 10);
    document.getElementById('pay-method').value = 'Cash';
    document.getElementById('pay-notes').value = '';

    document.getElementById('collect-payment-modal').classList.remove('hidden');
}

function openCollectBillForBill(billId) {
    const bill = AppState.bills.find(b => String(b.id) === String(billId));
    if (!bill) return;

    const cust = AppState.customers.find(c => String(c.id) === String(bill.customer_id));

    document.getElementById('pay-customer-id').value = bill.customer_id;
    document.getElementById('pay-customer-name-display').innerText = `${bill.customer_name || (cust ? cust.name : 'Customer')} (Bill #${bill.id})`;
    document.getElementById('pay-bill-id').value = bill.id;
    document.getElementById('pay-amount').value = bill.due_amount || bill.amount;
    document.getElementById('pay-date').value = new Date().toISOString().slice(0, 10);
    document.getElementById('pay-method').value = 'Cash';
    document.getElementById('pay-notes').value = `Payment for ${bill.month || bill.bill_month}`;

    document.getElementById('collect-payment-modal').classList.remove('hidden');
}

async function handlePaymentSubmit(e) {
    e.preventDefault();
    const customerId = document.getElementById('pay-customer-id').value;
    const billId = parseInt(document.getElementById('pay-bill-id').value) || 0;
    const amount = parseFloat(document.getElementById('pay-amount').value) || 0;
    const paymentDate = document.getElementById('pay-date').value;
    const method = document.getElementById('pay-method').value;
    const notes = document.getElementById('pay-notes').value.trim();

    if (amount <= 0) {
        showToast('Please enter a valid payment amount.', 'error');
        return;
    }

    const cust = AppState.customers.find(c => String(c.id) === String(customerId));
    const receiptNo = `REC-${Date.now().toString().slice(-6)}`;

    const paymentPayload = {
        id: Date.now(),
        customer_id: customerId,
        customer_name: cust ? cust.name : 'Customer',
        bill_id: billId,
        payment_receipt_no: receiptNo,
        amount: amount,
        payment_date: paymentDate,
        payment_method: method,
        notes: notes
    };

    setLoading(true);
    try {
        const res = await apiCall('payments.php', 'POST', paymentPayload);
        if (res.status) {
            showToast('Payment collected successfully!', 'success');
            document.getElementById('collect-payment-modal').classList.add('hidden');

            await Promise.all([fetchPayments(), fetchBills()]);
            renderPayments();
            renderBilling();
            renderDashboard();

            // Open Receipt Print View immediately
            displayMoneyReceipt({
                receipt_no: receiptNo,
                customer_name: cust ? cust.name : 'Customer',
                customer_code: cust ? (cust.customer_code || cust.id) : '',
                phone: cust ? cust.phone : '',
                amount: amount,
                payment_date: paymentDate,
                payment_method: method,
                notes: notes,
                bill_month: AppState.activeMonth
            });
        } else {
            showToast(res.message || 'Payment collection failed', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Error submitting payment', 'error');
    } finally {
        setLoading(false);
    }
}

function displayMoneyReceipt(receipt) {
    AppState.currentReceipt = receipt;
    renderReceiptHtml();
    document.getElementById('receipt-modal').classList.remove('hidden');
}

function renderReceiptHtml() {
    const r = AppState.currentReceipt;
    if (!r) return;

    const isThermal = AppState.receiptFormat === 'thermal';
    const container = document.getElementById('printable-receipt-area');

    if (isThermal) {
        container.className = 'thermal-receipt p-4 bg-white text-gray-900 border border-gray-200 mx-auto';
        container.innerHTML = `
            <div class="text-center pb-2 border-b border-dashed border-gray-400">
                <h2 class="font-bold text-base uppercase">${AppState.settings.isp_name || 'ISP BILLING'}</h2>
                <p class="text-xs text-gray-600">${AppState.settings.address || 'Broadband Internet Service'}</p>
                <p class="text-xs text-gray-600 font-semibold">Hotline: ${AppState.settings.hotline || 'Support'}</p>
                <p class="text-xs font-mono font-bold mt-1">*** MONEY RECEIPT ***</p>
            </div>
            <div class="py-2 text-xs border-b border-dashed border-gray-400 space-y-1">
                <div class="flex justify-between"><span>Receipt No:</span><span class="font-mono font-bold">${r.receipt_no}</span></div>
                <div class="flex justify-between"><span>Date:</span><span>${r.payment_date}</span></div>
                <div class="flex justify-between"><span>Customer:</span><span class="font-semibold">${r.customer_name}</span></div>
                <div class="flex justify-between"><span>Code / Phone:</span><span>${r.customer_code || ''} ${r.phone ? '(' + r.phone + ')' : ''}</span></div>
                <div class="flex justify-between"><span>Method:</span><span>${r.payment_method}</span></div>
            </div>
            <div class="py-3 text-center border-b border-dashed border-gray-400">
                <div class="text-xs text-gray-500 uppercase">Paid Amount</div>
                <div class="text-xl font-bold font-mono text-emerald-700">${formatCurrency(r.amount)}</div>
                ${r.notes ? `<div class="text-xs text-gray-500 mt-1 italic">${r.notes}</div>` : ''}
            </div>
            <div class="pt-3 text-center text-xs text-gray-500">
                <p>বিল পরিশোধের জন্য ধন্যবাদ!</p>
                <p class="font-mono text-[10px] text-gray-400 mt-1">Powered by ISP Billing Manager</p>
            </div>
        `;
    } else {
        container.className = 'a4-receipt p-8 bg-white text-gray-900 border border-gray-300 max-w-2xl mx-auto rounded-lg shadow-sm';
        container.innerHTML = `
            <div class="flex justify-between items-start border-b pb-4 mb-4">
                <div>
                    <h1 class="text-2xl font-bold text-blue-700">${AppState.settings.isp_name || 'ISP Billing Management'}</h1>
                    <p class="text-sm text-gray-600">${AppState.settings.address || 'Corporate Office'}</p>
                    <p class="text-sm text-gray-600">Hotline: <strong>${AppState.settings.hotline || ''}</strong></p>
                </div>
                <div class="text-right">
                    <span class="inline-block px-3 py-1 bg-emerald-100 text-emerald-800 text-sm font-bold rounded">MONEY RECEIPT</span>
                    <p class="text-xs font-mono text-gray-500 mt-2">Receipt No: <strong>${r.receipt_no}</strong></p>
                    <p class="text-xs text-gray-500">Date: ${r.payment_date}</p>
                </div>
            </div>

            <div class="bg-gray-50 p-4 rounded mb-6 text-sm">
                <div class="grid grid-cols-2 gap-4">
                    <div>
                        <span class="text-gray-500 block text-xs">Customer Name:</span>
                        <span class="font-bold text-gray-800 text-base">${r.customer_name}</span>
                        <div class="text-gray-600 text-xs">Code: ${r.customer_code || 'N/A'} | Phone: ${r.phone || 'N/A'}</div>
                    </div>
                    <div class="text-right">
                        <span class="text-gray-500 block text-xs">Payment Method:</span>
                        <span class="font-semibold text-gray-700">${r.payment_method}</span>
                    </div>
                </div>
            </div>

            <table class="w-full text-sm border border-gray-200 mb-6">
                <thead class="bg-gray-100 border-b">
                    <tr>
                        <th class="py-2 px-4 text-left">Description</th>
                        <th class="py-2 px-4 text-right">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td class="py-3 px-4 border-b">Internet Bill Subscription (${r.bill_month || 'Monthly Bill'})</td>
                        <td class="py-3 px-4 text-right font-bold border-b">${formatCurrency(r.amount)}</td>
                    </tr>
                </tbody>
                <tfoot>
                    <tr class="bg-gray-50">
                        <th class="py-3 px-4 text-left font-bold text-base">Total Received:</th>
                        <th class="py-3 px-4 text-right font-bold text-emerald-600 text-lg">${formatCurrency(r.amount)}</th>
                    </tr>
                </tfoot>
            </table>

            <div class="flex justify-between items-end pt-12 text-xs text-gray-600">
                <div class="border-t border-gray-400 w-48 text-center pt-1">Customer Signature</div>
                <div class="border-t border-gray-400 w-48 text-center pt-1">Authorized Seal & Signature</div>
            </div>
        `;
    }
}

function setReceiptFormat(format) {
    AppState.receiptFormat = format;
    document.getElementById('format-btn-thermal').className = format === 'thermal' ? 'px-3 py-1 text-xs font-semibold rounded bg-blue-600 text-white' : 'px-3 py-1 text-xs font-semibold rounded bg-gray-200 text-gray-700';
    document.getElementById('format-btn-a4').className = format === 'a4' ? 'px-3 py-1 text-xs font-semibold rounded bg-blue-600 text-white' : 'px-3 py-1 text-xs font-semibold rounded bg-gray-200 text-gray-700';
    renderReceiptHtml();
}

function printReceipt() {
    window.print();
}

function printReceiptDirect(paymentId) {
    const payment = AppState.payments.find(p => String(p.id) === String(paymentId));
    if (!payment) return;

    const cust = AppState.customers.find(c => String(c.id) === String(payment.customer_id));

    displayMoneyReceipt({
        receipt_no: payment.payment_receipt_no || `REC-${payment.id}`,
        customer_name: payment.customer_name || (cust ? cust.name : 'Customer'),
        customer_code: cust ? (cust.customer_code || cust.id) : '',
        phone: cust ? cust.phone : '',
        amount: payment.amount,
        payment_date: payment.payment_date,
        payment_method: payment.payment_method,
        notes: payment.notes || ''
    });
}

function renderPayments() {
    const tbody = document.getElementById('payments-tbody');
    const searchVal = (document.getElementById('payment-search-input')?.value || '').toLowerCase().trim();

    const filtered = AppState.payments.filter(p => {
        if (!searchVal) return true;
        return (p.payment_receipt_no && p.payment_receipt_no.toLowerCase().includes(searchVal)) ||
            (p.customer_name && p.customer_name.toLowerCase().includes(searchVal)) ||
            (p.payment_date && p.payment_date.includes(searchVal));
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="py-8 text-center text-gray-400">No payment records found.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(p => `
        <tr class="border-b dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-slate-800">
            <td class="py-3 px-4 font-mono text-xs font-semibold text-blue-600">${p.payment_receipt_no || p.id}</td>
            <td class="py-3 px-4 font-medium text-gray-900 dark:text-gray-100">${p.customer_name || 'N/A'}</td>
            <td class="py-3 px-4 font-bold text-emerald-600">${formatCurrency(p.amount)}</td>
            <td class="py-3 px-4 text-sm text-gray-600 dark:text-gray-300">${p.payment_date}</td>
            <td class="py-3 px-4 text-xs font-medium text-gray-600"><span class="px-2 py-0.5 bg-gray-100 rounded dark:bg-slate-700 dark:text-gray-300">${p.payment_method || 'Cash'}</span></td>
            <td class="py-3 px-4 text-right">
                <button onclick="printReceiptDirect(${p.id})" class="px-2 py-1 text-xs bg-gray-100 hover:bg-gray-200 text-gray-700 rounded dark:bg-slate-700 dark:text-gray-200">Print Receipt</button>
            </td>
        </tr>
    `).join('');
}

// ==========================================
// PACKAGES MANAGEMENT
// ==========================================
function renderPackages() {
    const grid = document.getElementById('packages-grid');
    if (AppState.packages.length === 0) {
        grid.innerHTML = `<div class="col-span-3 py-12 text-center text-gray-400">No packages created yet. Click "Add Package" above.</div>`;
        return;
    }

    grid.innerHTML = AppState.packages.map(p => `
        <div class="bg-white dark:bg-slate-800 p-6 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col justify-between hover:border-blue-300 transition">
            <div>
                <div class="flex justify-between items-center mb-2">
                    <h3 class="font-bold text-lg text-gray-800 dark:text-gray-100">${p.name}</h3>
                    <span class="px-2.5 py-0.5 text-xs font-bold bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200 rounded-full">${p.speed || 'Standard'}</span>
                </div>
                <div class="text-3xl font-extrabold text-blue-600 dark:text-blue-400 my-4">${formatCurrency(p.price)} <span class="text-xs font-normal text-gray-500">/ month</span></div>
            </div>
            <div class="pt-4 border-t dark:border-gray-700 flex justify-end space-x-2">
                <button onclick="openEditPackageModal('${p.id}')" class="px-3 py-1 text-xs bg-gray-100 text-gray-700 hover:bg-gray-200 rounded dark:bg-slate-700 dark:text-gray-300">Edit</button>
                <button onclick="deletePackage('${p.id}')" class="px-3 py-1 text-xs bg-rose-50 text-rose-600 hover:bg-rose-100 rounded">Delete</button>
            </div>
        </div>
    `).join('');
}

function openAddPackageModal() {
    document.getElementById('pkg-modal-title').innerText = 'Add New Package';
    document.getElementById('pkg-form').reset();
    document.getElementById('pkg-id').value = '';
    document.getElementById('package-modal').classList.remove('hidden');
}

function openEditPackageModal(pkgId) {
    const pkg = AppState.packages.find(p => String(p.id) === String(pkgId));
    if (!pkg) return;

    document.getElementById('pkg-modal-title').innerText = 'Edit Package';
    document.getElementById('pkg-id').value = pkg.id;
    document.getElementById('pkg-name').value = pkg.name;
    document.getElementById('pkg-price').value = pkg.price;
    document.getElementById('pkg-speed').value = pkg.speed || '';
    document.getElementById('package-modal').classList.remove('hidden');
}

async function handlePackageSave(e) {
    e.preventDefault();
    const id = document.getElementById('pkg-id').value || `pkg_${Date.now()}`;
    const name = document.getElementById('pkg-name').value.trim();
    const price = parseFloat(document.getElementById('pkg-price').value) || 0;
    const speed = document.getElementById('pkg-speed').value.trim();

    if (!name || price <= 0) {
        showToast('Please provide valid name and price.', 'error');
        return;
    }

    setLoading(true);
    try {
        const res = await apiCall('packages.php', 'POST', { id, name, price, speed });
        if (res.status) {
            showToast('Package saved successfully!', 'success');
            document.getElementById('package-modal').classList.add('hidden');
            await fetchPackages();
            renderPackages();
        } else {
            showToast(res.message || 'Failed to save package', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Error saving package', 'error');
    } finally {
        setLoading(false);
    }
}

async function deletePackage(pkgId) {
    if (!confirm('Are you sure you want to delete this package?')) return;
    setLoading(true);
    try {
        const res = await apiCall(`packages.php?id=${encodeURIComponent(pkgId)}`, 'DELETE');
        if (res.status) {
            showToast('Package deleted.', 'info');
            await fetchPackages();
            renderPackages();
        } else {
            showToast(res.message || 'Error deleting package', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Failed to delete package', 'error');
    } finally {
        setLoading(false);
    }
}

// ==========================================
// EXPENSE MANAGEMENT
// ==========================================
function renderExpenses() {
    const tbody = document.getElementById('expenses-tbody');
    const totalExp = AppState.expenses.reduce((sum, e) => sum + (parseFloat(e.amount) || 0), 0);
    document.getElementById('total-expenses-display').innerText = formatCurrency(totalExp);

    if (AppState.expenses.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="py-8 text-center text-gray-400">No expenses recorded.</td></tr>`;
        return;
    }

    tbody.innerHTML = AppState.expenses.map(e => `
        <tr class="border-b dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-slate-800">
            <td class="py-3 px-4 font-medium text-gray-900 dark:text-gray-100">${e.title}</td>
            <td class="py-3 px-4"><span class="px-2 py-0.5 text-xs bg-gray-100 text-gray-800 dark:bg-slate-700 dark:text-gray-300 rounded">${e.category || 'General'}</span></td>
            <td class="py-3 px-4 font-bold text-rose-600">${formatCurrency(e.amount)}</td>
            <td class="py-3 px-4 text-xs text-gray-500">${e.date}</td>
            <td class="py-3 px-4 text-xs text-gray-500">${e.payment_method || 'Cash'}</td>
            <td class="py-3 px-4 text-right">
                <button onclick="deleteExpense(${e.id})" class="px-2 py-1 text-xs text-rose-600 hover:underline">Delete</button>
            </td>
        </tr>
    `).join('');
}

function openAddExpenseModal() {
    document.getElementById('expense-form').reset();
    document.getElementById('exp-date').value = new Date().toISOString().slice(0, 10);
    document.getElementById('expense-modal').classList.remove('hidden');
}

async function handleExpenseSave(e) {
    e.preventDefault();
    const title = document.getElementById('exp-title').value.trim();
    const amount = parseFloat(document.getElementById('exp-amount').value) || 0;
    const category = document.getElementById('exp-category').value.trim();
    const date = document.getElementById('exp-date').value;
    const paymentMethod = document.getElementById('exp-method').value;
    const note = document.getElementById('exp-note').value.trim();

    if (!title || amount <= 0) {
        showToast('Please provide valid title and amount.', 'error');
        return;
    }

    const payload = {
        id: Date.now(),
        title,
        amount,
        category,
        date,
        payment_method: paymentMethod,
        note
    };

    setLoading(true);
    try {
        const res = await apiCall('expenses.php', 'POST', payload);
        if (res.status) {
            showToast('Expense recorded successfully!', 'success');
            document.getElementById('expense-modal').classList.add('hidden');
            await fetchExpenses();
            renderExpenses();
            renderDashboard();
        } else {
            showToast(res.message || 'Error saving expense', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Failed to save expense', 'error');
    } finally {
        setLoading(false);
    }
}

async function deleteExpense(id) {
    if (!confirm('Are you sure you want to delete this expense entry?')) return;
    setLoading(true);
    try {
        const res = await apiCall(`expenses.php?id=${id}`, 'DELETE');
        if (res.status) {
            showToast('Expense deleted.', 'info');
            await fetchExpenses();
            renderExpenses();
            renderDashboard();
        } else {
            showToast(res.message || 'Error deleting expense', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Failed to delete expense', 'error');
    } finally {
        setLoading(false);
    }
}

// ==========================================
// SETTINGS
// ==========================================
function renderSettings() {
    document.getElementById('set-isp-name').value = AppState.settings.isp_name || '';
    document.getElementById('set-hotline').value = AppState.settings.hotline || '';
    document.getElementById('set-address').value = AppState.settings.address || '';
    document.getElementById('set-currency').value = AppState.settings.currency_symbol || '৳';
    document.getElementById('set-theme').value = AppState.settings.theme_mode || 'LIGHT';
}

async function handleSettingsSave(e) {
    e.preventDefault();
    const ispName = document.getElementById('set-isp-name').value.trim();
    const hotline = document.getElementById('set-hotline').value.trim();
    const address = document.getElementById('set-address').value.trim();
    const currency = document.getElementById('set-currency').value.trim();
    const theme = document.getElementById('set-theme').value;

    const payload = {
        id: 1,
        isp_name: ispName,
        hotline,
        address,
        currency_symbol: currency,
        theme_mode: theme
    };

    setLoading(true);
    try {
        const res = await apiCall('settings.php', 'POST', payload);
        if (res.status) {
            showToast('Business settings updated!', 'success');
            AppState.settings = Object.assign(AppState.settings, payload);
            document.getElementById('brand-title').innerText = ispName;
            applyTheme(theme);
        } else {
            showToast(res.message || 'Failed to save settings', 'error');
        }
    } catch (err) {
        showToast(err.message || 'Error saving settings', 'error');
    } finally {
        setLoading(false);
    }
}

function applyTheme(mode) {
    if (mode === 'DARK') {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }
}

// ==========================================
// AUDIT LOGS
// ==========================================
async function loadAuditLogs() {
    const tbody = document.getElementById('logs-tbody');
    tbody.innerHTML = `<tr><td colspan="4" class="py-4 text-center text-gray-400">Loading audit history...</td></tr>`;

    try {
        const res = await apiCall('audit_logs.php');
        if (res.status && Array.isArray(res.data)) {
            if (res.data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="4" class="py-4 text-center text-gray-400">No activity logs recorded.</td></tr>`;
                return;
            }
            tbody.innerHTML = res.data.slice(0, 50).map(l => `
                <tr class="border-b dark:border-gray-700 text-xs">
                    <td class="py-2.5 px-4 font-mono">${new Date(parseInt(l.timestamp) || Date.now()).toLocaleString()}</td>
                    <td class="py-2.5 px-4 font-semibold text-blue-600">${l.action || 'ACTION'}</td>
                    <td class="py-2.5 px-4 text-gray-700 dark:text-gray-300">${l.details || l.action_type || ''}</td>
                    <td class="py-2.5 px-4"><span class="px-2 py-0.5 rounded text-[11px] bg-emerald-100 text-emerald-800">${l.status || 'SUCCESS'}</span></td>
                </tr>
            `).join('');
        }
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="4" class="py-4 text-center text-rose-500">Failed to load audit logs.</td></tr>`;
    }
}

// ==========================================
// INITIALIZATION
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    // Check initial auth state
    if (AppState.token) {
        showAppView();
        loadInitialData();
    } else {
        showAuthView();
    }

    // Set billing month selector default
    const monthFilter = document.getElementById('billing-month-filter');
    if (monthFilter) {
        monthFilter.value = AppState.activeMonth;
        monthFilter.addEventListener('change', (e) => {
            AppState.activeMonth = e.target.value;
            renderBilling();
            renderDashboard();
        });
    }

    // Customer search debounce
    const custSearch = document.getElementById('customer-search-input');
    if (custSearch) {
        custSearch.addEventListener('input', () => renderCustomers());
    }

    const custStatusFilter = document.getElementById('customer-filter-status');
    if (custStatusFilter) {
        custStatusFilter.addEventListener('change', () => renderCustomers());
    }

    const custPkgFilter = document.getElementById('customer-filter-pkg');
    if (custPkgFilter) {
        custPkgFilter.addEventListener('change', () => renderCustomers());
    }
});
