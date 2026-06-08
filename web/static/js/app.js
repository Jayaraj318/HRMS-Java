// Shared frontend JS for login and employees page
document.addEventListener('DOMContentLoaded', () => {
    // fetch current user and apply role-based UI adjustments
    window.currentUser = null;
    // helper to remove admin-only links and enforce page-level redirects
    function applyRoleRestrictions(user) {
        const isAdmin = user && user.role === 'ADMIN';
        // use the correct selector for the sidebar links
        document.querySelectorAll('aside nav a').forEach(a => {
            const href = a.getAttribute('href') || '';
            if (!isAdmin && (href.indexOf('employees.html') !== -1 || href.indexOf('departments.html') !== -1)) {
                // remove link entirely so it can't be clicked or focused
                a.remove();
            }
        });
        // page-level protection: if non-admin opens admin pages, redirect to dashboard
        const path = window.location.pathname || '';
        if (!isAdmin && (path.endsWith('/static/employees.html') || path.endsWith('/employees.html') || path.endsWith('/static/departments.html') || path.endsWith('/departments.html'))) {
            window.location = '/static/dashboard.html';
        }
    }
    if (!window.location.pathname.includes('index.html')) {

        (async () => {
            try {
                const r = await fetch('/api/me');
                if (r.ok) window.currentUser = await r.json();
            } catch (e) { /* ignore */ }
            // apply role-based UI changes after /api/me is loaded
            applyRoleRestrictions(window.currentUser);
        })();
    }
    // Login page
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fd = new FormData(loginForm);
            const body = new URLSearchParams(fd);
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body
            });
            const data = await res.json().catch(() => ({}));
            if (res.ok) {
                try {
                    const meR = await fetch('/api/me');
                    if (meR.ok) window.currentUser = await meR.json();
                } catch (e) { /* ignore */ }
                // update sidebar immediately for the logged-in user
                try { applyRoleRestrictions(window.currentUser); } catch (e) { /* ignore */ }
                window.location = '/static/dashboard.html';
            } else {
                document.getElementById('msg').textContent = data.message || 'Invalid Username or Password';
            }
        });
    }

    // Employees page
    const addBtn = document.getElementById('addBtn');
    const modal = document.getElementById('modal');
    const empForm = document.getElementById('empForm');
    const cancel = document.getElementById('cancel');
    const search = document.getElementById('search');

    if (addBtn) {
        addBtn.addEventListener('click', () => {
            openModal();
        });
    }
    if (cancel) cancel.addEventListener('click', () => closeModal());

    if (empForm) {
        empForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            // client-side validation
            const empId = empForm.querySelector('input[name="empId"]').value.trim();
            const fullName = empForm.querySelector('input[name="fullName"]').value.trim();
            const email = empForm.querySelector('input[name="email"]').value.trim();
            if (!empId || !fullName || !email) { alert('Emp ID, Full Name and Email are required'); return; }
            const emailRe = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
            if (!emailRe.test(email)) { alert('Enter a valid email'); return; }
            const fd = new FormData(empForm); const body = new URLSearchParams(fd);
            const id = empForm.querySelector('input[name="id"]').value;
            const action = id ? 'update' : 'add';
            body.append('action', action);
            const res = await fetch('/api/employees', { method: 'POST', body });
            const data = await res.json().catch(() => ({}));
            if (res.ok && data.status === 'ok') { loadEmployees(); closeModal(); }
            else { alert(data.message || 'Failed'); }
        });
    }

    if (search) {
        let t;
        search.addEventListener('input', () => { clearTimeout(t); t = setTimeout(loadEmployees, 300); });
    }

    if (document.getElementById('empTable')) loadEmployees();
    if (document.getElementById('deptTable')) loadDepartments();

    document.getElementById('logout')?.addEventListener('click', async (e) => {
        e.preventDefault(); await fetch('/api/logout'); window.location = '/static/index.html';
    });

    // Attendance page init
    const attDate = document.getElementById('attDate');
    if (attDate) {
        const today = new Date().toISOString().slice(0, 10);
        attDate.value = today;
        document.getElementById('refreshAtt')?.addEventListener('click', loadAttendance);
        attDate.addEventListener('change', loadAttendance);
        loadAttendance();
        const tbody = document.querySelector('#attTable tbody');
        if (tbody && !tbody._attListenerAttached) {
            tbody.addEventListener('click', async (ev) => {
                const btn = ev.target.closest('button');
                if (!btn) return;
                const action = btn.getAttribute('data-action');
                const id = btn.getAttribute('data-id');
                if (!action || !id) return;
                console.log('[Attendance] button clicked', { action, id });
                try {
                    const body = new URLSearchParams(); body.append('employeeId', id); body.append('action', action);
                    console.log('[Attendance] sending POST', { url: '/api/attendance', body: body.toString() });
                    const r = await fetch('/api/attendance', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body });
                    console.log('[Attendance] response', r.status);
                    if (!r.ok) { const t = await r.text(); console.error('[Attendance] action failed', t); alert(t || 'Action failed'); return; }
                    await loadAttendance();
                } catch (err) { console.error('[Attendance] action error', err); alert(err.message || err); }
            });
            tbody._attListenerAttached = true;
        }
    }
});

async function loadEmployees() {
    const q = document.getElementById('search')?.value || '';
    const url = '/api/employees' + (q ? ('?search=' + encodeURIComponent(q)) : '');
    const res = await fetch(url);
    const list = await res.json();
    const tbody = document.querySelector('#empTable tbody');
    tbody.innerHTML = '';
    list.forEach(e => {
        const tr = document.createElement('tr');
tr.innerHTML = `
<td>${e.id}</td>
<td>${e.empId}</td>
<td>${e.fullName}</td>
<td>${e.email}</td>
<td>${e.phone || ''}</td>
<td>${e.designation || ''}</td>
<td>${e.department || ''}</td>
<td>
<button data-id="${e.id}" class="btn edit-btn edit">
✏ Edit
</button>

<button data-id="${e.id}" class="btn delete-btn del">
🗑 Delete
</button>
</td>`;
        tbody.appendChild(tr);
    });
    document.querySelectorAll('.edit').forEach(b => b.addEventListener('click', async (ev) => {
        const id = ev.target.dataset.id;
        const res = await fetch('/api/employees?id=' + encodeURIComponent(id));
        if (!res.ok) { alert('Failed to load employee'); return; }
        const e = await res.json();
        openModal(e);
    }));
    document.querySelectorAll('.del').forEach(b => b.addEventListener('click', async (ev) => {
        if (!confirm('Delete employee?')) return;
        const id = ev.target.dataset.id;
        const body = new URLSearchParams(); body.append('action', 'delete'); body.append('id', id);
        const res = await fetch('/api/employees', { method: 'POST', body });
        if (res.ok) loadEmployees(); else alert('Delete failed');
    }));
}

function openModal(data) {
    document.getElementById('modal').classList.remove('hidden');
    const form = document.getElementById('empForm');
    form.reset();
    if (data) {
        // support both camelCase and snake_case JSON keys
        const get = (obj, camel, snake) => {
            if (!obj) return '';
            if (camel in obj && obj[camel] !== undefined && obj[camel] !== null) return obj[camel];
            if (snake in obj && obj[snake] !== undefined && obj[snake] !== null) return obj[snake];
            return '';
        };

        form.querySelector('input[name="id"]').value = get(data, 'id', 'id');
        form.querySelector('input[name="empId"]').value = get(data, 'empId', 'emp_id');
        form.querySelector('input[name="fullName"]').value = get(data, 'fullName', 'full_name');
        form.querySelector('input[name="email"]').value = get(data, 'email', 'email');
        form.querySelector('input[name="phone"]').value = get(data, 'phone', 'phone');
        form.querySelector('input[name="designation"]').value = get(data, 'designation', 'designation');
        form.querySelector('input[name="department"]').value = get(data, 'department', 'department');
        // joiningDate can be 'joiningDate' or 'joining_date'; ensure YYYY-MM-DD format
        const jd = get(data, 'joiningDate', 'joining_date');
        form.querySelector('input[name="joiningDate"]').value = jd ? jd.substring(0, 10) : '';
        form.querySelector('input[name="salary"]').value = get(data, 'salary', 'salary') || 0;
    }
}
function closeModal() { document.getElementById('modal').classList.add('hidden'); }

// Departments functionality
async function loadDepartments() {
    const res = await fetch('/api/departments');
    console.log('departments API status', res.status);
    const list = await res.json();
    console.log('departments API data', list);
    const tbody = document.querySelector('#deptTable tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    list.forEach(d => {
        const name = d.departmentName || d.department_name || '';
        const mgr = d.managerName || d.manager_name || '';
        const created = d.createdAt || d.created_at || '';
        const tr = document.createElement('tr');
tr.innerHTML = `
<td>${d.id}</td>
<td>${name}</td>
<td>${mgr}</td>
<td>${created}</td>
<td>
<button data-id="${d.id}" class="btn edit-btn edit-dept">
✏ Edit
</button>

<button data-id="${d.id}" class="btn delete-btn del-dept">
🗑 Delete
</button>
</td>`;
        tbody.appendChild(tr);
    });
    document.querySelectorAll('.edit-dept').forEach(b => b.addEventListener('click', async (ev) => {
        const id = ev.target.dataset.id;
        const res = await fetch('/api/departments?id=' + encodeURIComponent(id));
        if (!res.ok) { alert('Failed to load department'); return; }
        const d = await res.json();
        openDeptModal(d);
    }));
    document.querySelectorAll('.del-dept').forEach(b => b.addEventListener('click', async (ev) => {
        if (!confirm('Delete department?')) return;
        const id = ev.target.dataset.id;
        const body = new URLSearchParams(); body.append('action', 'delete'); body.append('id', id);
        const res = await fetch('/api/departments', { method: 'POST', body });
        if (res.ok) loadDepartments(); else alert('Delete failed');
    }));
}

function openDeptModal(data) {
    document.getElementById('deptModal').classList.remove('hidden');
    const form = document.getElementById('deptForm');
    form.reset();
    if (data) {
        const get = (obj, camel, snake) => {
            if (!obj) return '';
            if (camel in obj && obj[camel] !== undefined && obj[camel] !== null) return obj[camel];
            if (snake in obj && obj[snake] !== undefined && obj[snake] !== null) return obj[snake];
            return '';
        };
        form.querySelector('input[name="id"]').value = get(data, 'id', 'id');
        form.querySelector('input[name="departmentName"]').value = get(data, 'departmentName', 'department_name');
        form.querySelector('input[name="managerName"]').value = get(data, 'managerName', 'manager_name');
    }
}

function closeDeptModal() { document.getElementById('deptModal').classList.add('hidden'); }

// init department UI handlers
document.addEventListener('DOMContentLoaded', () => {
    const addDeptBtn = document.getElementById('addDeptBtn');
    const deptCancel = document.getElementById('deptCancel');
    const deptForm = document.getElementById('deptForm');
    if (addDeptBtn) addDeptBtn.addEventListener('click', () => openDeptModal());
    if (deptCancel) deptCancel.addEventListener('click', () => closeDeptModal());
    if (deptForm) {
        deptForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = deptForm.querySelector('input[name="departmentName"]').value.trim();
            if (!name) { alert('Department name required'); return; }
            const fd = new FormData(deptForm); const body = new URLSearchParams(fd);
            const id = deptForm.querySelector('input[name="id"]').value;
            const action = id ? 'update' : 'add'; body.append('action', action);
            const res = await fetch('/api/departments', { method: 'POST', body });
            const data = await res.json().catch(() => ({}));
            if (res.ok && data.status === 'ok') { loadDepartments(); closeDeptModal(); }
            else { alert(data.message || 'Failed'); }
        });
    }
});

// Attendance loader
async function loadAttendance() {
    const dateEl = document.getElementById('attDate');
    if (!dateEl) return;
    const date = dateEl.value;
    const tbody = document.querySelector('#attTable tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    try {
        const res = await fetch(`/api/attendance?date=${encodeURIComponent(date)}`);
        if (!res.ok) throw new Error('Failed to load attendance');
        const data = await res.json();
        const rows = [];
        if (window.currentUser && window.currentUser.role === 'ADMIN') {
            // admin: attempt to show all employees with attendance data
            let employees = [];
            try { const r = await fetch('/api/employees'); if (r.ok) employees = await r.json(); } catch (e) { employees = []; }
            const map = new Map();
            data.forEach(raw => {
                const eid = raw.employeeId || raw.employee_id || raw.employee;
                map.set(String(eid), raw);
            });
            if (employees.length) {
                employees.forEach(emp => {
                    const rec = map.get(String(emp.id)) || { employeeId: emp.id, empId: emp.empId, employeeName: emp.fullName || emp.name, checkIn: null, checkOut: null, status: 'Absent' };
                    rows.push({ emp, rec });
                });
            } else {
                data.forEach(raw => {
                    const emp = { id: raw.employeeId || raw.employee_id || raw.employee, empId: raw.empId || raw.emp_id || '', fullName: raw.employeeName || raw.name || '' };
                    rows.push({ emp, rec: raw });
                });
            }
        } else {
            // employee: only their own records
            data.forEach(raw => {
                const emp = { id: raw.employeeId || raw.employee_id || raw.employee, empId: raw.empId || raw.emp_id || '', fullName: raw.employeeName || raw.name || '' };
                rows.push({ emp, rec: raw });
            });
            if (rows.length === 0 && window.currentUser && window.currentUser.employeeId) {
                try {
                    // For non-admins, use /api/me instead of calling /api/employees (RBAC)
                    const r = await fetch('/api/me');
                    if (r.ok) {
                        const emp = await r.json();
                        rows.push({ emp: { id: emp.employeeId || emp.id, empId: emp.empId || '', fullName: emp.fullName || emp.username || '' }, rec: { employeeId: emp.employeeId || emp.id, empId: emp.empId || '', employeeName: emp.fullName || emp.username || '', checkIn: null, checkOut: null, status: 'Absent' } });
                    }
                } catch (e) { }
            }
        }

        rows.forEach(({ emp, rec }) => {
            const tr = document.createElement('tr');
            const displayEmpId = emp.empId || emp.emp_id || rec.empId || '';
            const displayName = emp.fullName || emp.name || rec.employeeName || '';
            const displayCheckIn = rec.checkIn || rec.checkInTime || rec.check_in || rec.check_in_time || '';
            const displayCheckOut = rec.checkOut || rec.checkOutTime || rec.check_out || rec.check_out_time || '';
            const displayStatus = rec.status || (displayCheckIn ? 'Present' : 'Absent');
            let actions = '';
            if (window.currentUser && window.currentUser.role === 'ADMIN') {
                actions = `<button class="btn" data-action="checkin" data-id="${emp.id}">Check In</button> <button class="btn" data-action="checkout" data-id="${emp.id}">Check Out</button>`;
            } else if (window.currentUser && window.currentUser.employeeId && window.currentUser.employeeId === emp.id) {
                actions = `<button class="btn" data-action="checkin" data-id="${emp.id}">Check In</button> <button class="btn" data-action="checkout" data-id="${emp.id}">Check Out</button>`;
            }
            tr.innerHTML = `<td>${displayEmpId}</td><td>${displayName}</td><td>${displayCheckIn}</td><td>${displayCheckOut}</td><td>${displayStatus}</td><td>${actions}</td>`;
            tbody.appendChild(tr);
        });
        if (!tbody._attListenerAttached) {
            tbody.addEventListener('click', async (ev) => {
                const btn = ev.target.closest('button');
                if (!btn) return;
                const action = btn.getAttribute('data-action');
                const id = btn.getAttribute('data-id');
                if (!action || !id) return;
                try {
                    const body = new URLSearchParams(); body.append('employeeId', id); body.append('action', action);
                    const r = await fetch('/api/attendance', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body });
                    if (!r.ok) { const t = await r.text(); alert(t || 'Action failed'); return; }
                    await loadAttendance();
                } catch (err) { alert(err.message || err); }
            });
            tbody._attListenerAttached = true;
        }
    } catch (err) { console.error(err); }
}
