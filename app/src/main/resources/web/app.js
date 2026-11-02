const elements = {
  modal: document.querySelector('#connect-modal'),
  connectForm: document.querySelector('#connect-form'),
  connectError: document.querySelector('#connect-error'),
  connectLabel: document.querySelector('#connect-label'),
  rows: document.querySelector('#vehicle-rows'),
  detail: document.querySelector('#detail-panel'),
  search: document.querySelector('#search-input'),
  type: document.querySelector('#type-filter'),
  status: document.querySelector('#status-filter'),
  tablePicker: document.querySelector('#table-picker'),
  tableSearch: document.querySelector('#table-search'),
  tableHead: document.querySelector('#all-table-head'),
  tableBody: document.querySelector('#all-table-body'),
  overviewMaintenance: document.querySelector('#overview-maintenance'),
  overviewStatus: document.querySelector('#overview-status'),
  fuelLatest: document.querySelector('#fuel-latest'),
  toast: document.querySelector('#toast')
};

let activeVehicleId = null;
let vehicleRows = [];
let previewMode = false;
let toastTimer;
let searchTimer;
let selectedTableRows = [];

const todayLabel = document.querySelector('#today-label');
if (todayLabel) {
  todayLabel.textContent = new Intl.DateTimeFormat('en', {
    weekday: 'short', month: 'short', day: 'numeric', year: 'numeric'
  }).format(new Date()).toUpperCase();
}

const openConnectButton = document.querySelector('#open-connect');
const topConnectButton = document.querySelector('#top-connect');
const closeConnectButton = document.querySelector('#close-connect');
const refreshButton = document.querySelector('#refresh-button');

if (openConnectButton) openConnectButton.addEventListener('click', openConnect);
if (topConnectButton) topConnectButton.addEventListener('click', openConnect);
if (closeConnectButton) closeConnectButton.addEventListener('click', closeConnect);
if (refreshButton) refreshButton.addEventListener('click', refreshAll);
if (elements.search) {
  elements.search.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadVehicles, 180);
  });
}
if (elements.type) elements.type.addEventListener('change', loadVehicles);
if (elements.status) elements.status.addEventListener('change', loadVehicles);
if (elements.tablePicker) elements.tablePicker.addEventListener('change', loadSelectedTable);
if (elements.tableSearch) elements.tableSearch.addEventListener('input', renderSelectedTable);
if (elements.connectForm) elements.connectForm.addEventListener('submit', submitConnection);
if (elements.modal) {
  elements.modal.addEventListener('click', event => {
    if (event.target === elements.modal) closeConnect();
  });
}
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') closeConnect();
});

function injectSidebarTableLinks() {
  const nav = document.querySelector('.main-nav');
  if (!nav || nav.querySelector('.nav-subgroup')) return;

  const tableNames = [
    'CUSTOMER4', 'DRIVER', 'VEHICLE', 'SERVICE_CENTER', 'BIKE', 'CAR', 'TRUCK', 'BUS',
    'VEHICLE_OWNERSHIP', 'MAINTENANCE', 'PAYMENT', 'TRIP', 'WARRANTY', 'ACCIDENT',
    'INSURANCE', 'FUEL_RECORD'
  ];

  const group = document.createElement('div');
  group.className = 'nav-subgroup';

  const label = document.createElement('div');
  label.className = 'nav-subgroup-label';
  label.textContent = 'TABLES';
  group.appendChild(label);

  tableNames.forEach(name => {
    const link = document.createElement('a');
    const slug = name === 'CUSTOMER4' ? 'customers' : name.toLowerCase().replace(/_/g, '-');
    link.href = `/${slug}.html`;
    link.className = 'nav-table-link';
    link.textContent = name === 'CUSTOMER4' ? 'CUSTOMERS' : name;
    if (window.location.pathname === `/${slug}.html`) {
      link.classList.add('selected');
    }
    group.appendChild(link);
  });

  nav.appendChild(group);
}

function configureSingleTablePage() {
  if (window.location.pathname === '/tables.html') return;
  const picker = document.querySelector('.table-picker');
  if (picker) picker.hidden = true;
}

function openConnect() {
  elements.connectError.textContent = '';
  elements.modal.classList.add('open');
  elements.modal.setAttribute('aria-hidden', 'false');
  elements.connectForm.elements.username.focus();
}

function closeConnect() {
  elements.modal.classList.remove('open');
  elements.modal.setAttribute('aria-hidden', 'true');
}

async function request(url, options = {}) {
  const response = await fetch(url, options);
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || 'The request failed.');
  return payload;
}

async function submitConnection(event) {
  event.preventDefault();
  const button = elements.connectForm.querySelector('button[type="submit"]');
  const form = new URLSearchParams(new FormData(elements.connectForm));
  elements.connectError.textContent = '';
  button.disabled = true;
  elements.connectLabel.textContent = 'Connecting...';
  try {
    await request('/api/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: form.toString()
    });
    elements.connectForm.elements.password.value = '';
    closeConnect();
    setConnection(true, 'Oracle connected');
    showToast('Connected to the project database.');
    await refreshAll();
  } catch (error) {
    elements.connectError.textContent = error.message;
  } finally {
    button.disabled = false;
    elements.connectLabel.textContent = 'Connect to Oracle';
  }
}

function setConnection(connected, message, demo = false) {
  previewMode = demo;
  const overviewSource = document.querySelector('#overview-source');
  const fuelSource = document.querySelector('#fuel-source');
  if (overviewSource) {
    overviewSource.textContent = connected ? 'LIVE DATABASE VALUES' : demo ? 'SAMPLE VALUES FROM PROJECT SQL' : 'DATABASE NOT CONNECTED';
    overviewSource.classList.toggle('source-live', connected);
    overviewSource.classList.toggle('source-preview', demo);
  }
  if (fuelSource) fuelSource.textContent = connected ? 'live database' : demo ? 'project sample data' : 'database unavailable';
  const sidebarDot = document.querySelector('#sidebar-dot');
  const topDot = document.querySelector('#top-dot');
  if (sidebarDot) {
    sidebarDot.classList.toggle('online', connected);
    sidebarDot.classList.toggle('preview', demo);
  }
  if (topDot) {
    topDot.classList.toggle('online', connected);
    topDot.classList.toggle('preview', demo);
  }
  const sidebarStatus = document.querySelector('#sidebar-status');
  const topStatus = document.querySelector('#top-status');
  if (sidebarStatus) {
    sidebarStatus.textContent = connected ? 'Connected' : demo ? 'Sample data preview' : 'Not connected';
  }
  if (topStatus) {
    topStatus.textContent = connected ? 'Oracle connected' : demo ? 'Sample data preview' : 'Connect database';
  }
  const fleetCount = document.querySelector('#fleet-count');
  if (!connected && message && fleetCount) fleetCount.textContent = message;
}

async function checkStatus() {
  try {
    const status = await request('/api/status');
    setConnection(status.connected, status.message, status.demo === true);
    const fleetCount = document.querySelector('#fleet-count');
    if (status.demo && fleetCount) fleetCount.textContent = 'Sample records from sql/02_insert_data.sql';
    else if (!status.connected && fleetCount) fleetCount.textContent = 'Database connection required';
    return status.connected || status.demo === true;
  } catch (error) {
    setConnection(false, error.message);
    return false;
  }
}

async function refreshAll() {
  if (elements.tablePicker && elements.tableSearch && elements.tableHead && elements.tableBody) {
    await loadTableNames();
  }
  const connected = await checkStatus();
  const hasVehicleDashboard = !!(elements.search && elements.rows && elements.detail && elements.type && elements.status);
  const hasSummaryCards = !!(document.querySelector('#metric-vehicles') || document.querySelector('#metric-active') || document.querySelector('#metric-maintenance') || document.querySelector('#metric-trips') || document.querySelector('#metric-accidents'));
  const hasTypeBreakdown = !!document.querySelector('#type-list');

  if (hasVehicleDashboard) {
    await Promise.all([loadSummary(), loadVehicles()]);
    return;
  }

  if (hasSummaryCards || hasTypeBreakdown || elements.overviewMaintenance || elements.overviewStatus || elements.fuelLatest) {
    await Promise.all([loadSummary(), loadOverviewPanels()]);
    return;
  }

  if (!connected) {
    await loadSummary();
  }
}

async function loadTableNames() {
  if (!elements.tablePicker || !elements.tableSearch || !elements.tableHead || !elements.tableBody) return;
  try {
    const tables = await request('/api/tables');
    const previous = elements.tablePicker.value;
    elements.tablePicker.replaceChildren();
    tables.forEach(table => {
      const option = document.createElement('option');
      option.value = table.name;
      option.textContent = table.name;
      elements.tablePicker.append(option);
    });
    const pageSlug = window.location.pathname.split('/').pop().replace(/\.html$/, '');
    const pageTable = tables.find(table => {
      const slug = table.name === 'CUSTOMER4' ? 'customers' : table.name.toLowerCase().replace(/_/g, '-');
      return slug === pageSlug;
    });
    const selectedTable = pageTable ? pageTable.name : previous;
    if (selectedTable && tables.some(table => table.name === selectedTable)) {
      elements.tablePicker.value = selectedTable;
    }
    await loadSelectedTable();
  } catch (error) {
    const tableDescription = document.querySelector('#table-description');
    if (tableDescription) tableDescription.textContent = error.message;
    elements.tableBody.innerHTML = `<tr><td class="empty-state">${escapeHtml(error.message)}</td></tr>`;
  }
}

async function loadSelectedTable() {
  if (!elements.tablePicker || !elements.tableSearch || !elements.tableHead || !elements.tableBody) return;
  const tableName = elements.tablePicker.value;
  if (!tableName) return;
  const tableDescription = document.querySelector('#table-description');
  const vehicleId = new URLSearchParams(window.location.search).get('vehicle_id');
    if (vehicleId && !vehicleId.match(/^\d+$/)) {
    if (tableDescription) tableDescription.textContent = 'Vehicle ID must be a positive number.';
      return;
    }
  if (tableDescription) tableDescription.textContent = `Loading ${tableName}${vehicleId ? ` records for vehicle ${vehicleId}` : ''}...`;
  try {
    const tableUrl = new URL(`/api/tables/${encodeURIComponent(tableName)}`, window.location.origin);
    if (vehicleId) tableUrl.searchParams.set('vehicle_id', vehicleId);
    const result = await request(`${tableUrl.pathname}${tableUrl.search}`);
    selectedTableRows = result.rows || [];
    const tableCount = document.querySelector('#table-count');
    if (tableCount) tableCount.textContent = `${number(result.count)} rows${result.demo ? ' · SAMPLE PREVIEW' : ' · LIVE ORACLE'}`;
    if (tableDescription) tableDescription.textContent = `${result.table}${result.vehicleId ? ` · vehicle ${result.vehicleId}` : ''} · ${selectedTableRows.length} ${selectedTableRows.length === 1 ? 'record' : 'records'}${result.demo ? ' from the SQL sample data' : ' from Oracle'}`;
    renderSelectedTable();
  } catch (error) {
    selectedTableRows = [];
    const tableCount = document.querySelector('#table-count');
    if (tableCount) tableCount.textContent = 'Unable to load table';
    if (tableDescription) tableDescription.textContent = error.message;
    elements.tableHead.replaceChildren();
    elements.tableBody.innerHTML = `<tr><td class="empty-state">${escapeHtml(error.message)}</td></tr>`;
  }
}

function renderSelectedTable() {
  const search = elements.tableSearch.value.trim().toLocaleLowerCase();
  const rows = selectedTableRows.filter(row => !search || Object.values(row).some(value =>
    String(value == null ? '' : value).toLocaleLowerCase().includes(search)));
  elements.tableHead.replaceChildren();
  elements.tableBody.replaceChildren();
  if (!selectedTableRows.length) {
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.className = 'empty-state';
    cell.textContent = 'No records in this table.';
    row.append(cell);
    elements.tableBody.append(row);
    return;
  }
  const columns = Object.keys(selectedTableRows[0]);
  const headerRow = document.createElement('tr');
  columns.forEach(column => {
    const heading = document.createElement('th');
    heading.scope = 'col';
    heading.textContent = column;
    headerRow.append(heading);
  });
  elements.tableHead.append(headerRow);
  if (!rows.length) {
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.colSpan = columns.length;
    cell.className = 'empty-state';
    cell.textContent = 'No rows match this search.';
    row.append(cell);
    elements.tableBody.append(row);
    return;
  }
  rows.forEach(record => {
    const row = document.createElement('tr');
    columns.forEach(column => {
      const cell = document.createElement('td');
      const value = record[column];
      cell.textContent = value == null ? '—' : String(value);
      if (value != null) cell.title = String(value);
      row.append(cell);
    });
    elements.tableBody.append(row);
  });
}

async function loadSummary() {
  try {
    const summary = await request('/api/summary');
    const metricVehicles = document.querySelector('#metric-vehicles');
    const metricActive = document.querySelector('#metric-active');
    const metricMaintenance = document.querySelector('#metric-maintenance');
    const metricTrips = document.querySelector('#metric-trips');
    const metricAccidents = document.querySelector('#metric-accidents');
    const metricFuel = document.querySelector('#metric-fuel');
    if (metricVehicles) metricVehicles.textContent = number(summary.vehicles);
    if (metricActive) metricActive.textContent = number(summary.active);
    if (metricMaintenance) metricMaintenance.textContent = number(summary.maintenance);
    if (metricTrips) metricTrips.textContent = number(summary.trips);
    if (metricAccidents) metricAccidents.textContent = number(summary.accidents);
    if (metricFuel) metricFuel.textContent = number(summary.fuelEntries);
    renderTypes(summary.types || [], summary.vehicles || 0);
  } catch (error) {
    showToast(error.message);
  }
}

async function loadVehicles() {
  if (!elements.rows || !elements.detail || !elements.search || !elements.type || !elements.status) return;
  const params = new URLSearchParams({
    search: elements.search.value.trim(),
    type: elements.type.value,
    status: elements.status.value
  });
  try {
    vehicleRows = await request(`/api/vehicles?${params.toString()}`);
    renderVehicles(vehicleRows);
    const fleetCount = document.querySelector('#fleet-count');
    if (fleetCount) fleetCount.textContent = `${vehicleRows.length}${previewMode ? ' sample' : ''} ${vehicleRows.length === 1 ? 'vehicle' : 'vehicles'}`;
    if (activeVehicleId && vehicleRows.some(row => Number(row.VEHICLE_ID) === activeVehicleId)) {
      await loadDetails(activeVehicleId);
    } else if (vehicleRows.length) {
      await loadDetails(Number(vehicleRows[0].VEHICLE_ID));
    } else {
      activeVehicleId = null;
      elements.detail.innerHTML = emptyDetail('No vehicles match the selected filters.');
    }
  } catch (error) {
    elements.rows.innerHTML = `<tr><td colspan="6" class="empty-state">${escapeHtml(error.message)}</td></tr>`;
  }
}

function renderVehicles(rows) {
  if (!rows.length) {
    elements.rows.innerHTML = '<tr><td colspan="6" class="empty-state">No vehicles found.</td></tr>';
    return;
  }
  elements.rows.innerHTML = rows.map(row => {
    const id = Number(row.VEHICLE_ID);
    const active = id === activeVehicleId ? ' active' : '';
    return `<tr class="vehicle-row${active}" data-vehicle-id="${id}">
      <td><span class="vehicle-name">${escapeHtml(row.MODEL)}</span><span class="vehicle-brand">${escapeHtml(row.BRAND)}</span></td>
      <td class="registration">${escapeHtml(row.REGISTRATION_NO)}</td>
      <td><span class="type-label">${escapeHtml(row.VEHICLE_TYPE)}</span></td>
      <td><span class="status-label ${statusClass(row.STATUS)}">${escapeHtml(row.STATUS || 'Unknown')}</span></td>
      <td>${escapeHtml(row.MANUFACTURING_YEAR)}</td>
      <td class="record-count">${number(row.MAINTENANCE_COUNT)} svc · ${number(row.ACCIDENT_COUNT)} acc</td>
    </tr>`;
  }).join('');
  elements.rows.querySelectorAll('.vehicle-row').forEach(row => {
    row.addEventListener('click', () => loadDetails(Number(row.dataset.vehicleId)));
  });
}

async function loadDetails(vehicleId) {
  activeVehicleId = vehicleId;
  renderVehicles(vehicleRows);
  try {
    const details = await request(`/api/vehicles/${vehicleId}`);
    renderDetails(details);
    await loadVehicleProcedureReport(vehicleId);
  } catch (error) {
    elements.detail.innerHTML = emptyDetail(error.message);
  }
}

async function loadVehicleProcedureReport(vehicleId) {
  const container = elements.detail.querySelector('#procedure-report');
  if (!container) return;
  if (previewMode) {
    container.innerHTML = '<div class="report-unavailable">Connect the Oracle project schema to run the stored procedures. Sample preview does not execute PL/SQL.</div>';
    return;
  }
  container.innerHTML = '<div class="report-loading">Running vehicle PL/SQL procedures...</div>';
  try {
    const result = await request(`/api/vehicles/${vehicleId}/report`);
    if (activeVehicleId !== vehicleId) return;
    container.replaceChildren();
    (result.reports || []).forEach(report => {
      const section = document.createElement('section');
      section.className = 'procedure-report-section';
      const heading = document.createElement('h4');
      heading.textContent = report.procedure.replaceAll('_', ' ');
      const output = document.createElement('pre');
      output.textContent = (report.lines || []).join('\n') || 'Procedure returned no output.';
      section.append(heading, output);
      container.append(section);
    });
  } catch (error) {
    if (activeVehicleId !== vehicleId) return;
    container.innerHTML = `<div class="report-unavailable">${escapeHtml(error.message)}</div>`;
  }
}

function renderDetails(data) {
  const vehicle = data.vehicle;
  const sections = [
    ['ownership', 'Owners', 'ownership'],
    ['insurance', 'Insurance', 'insurance'],
    ['warranty', 'Warranty', 'warranty'],
    ['maintenance', 'Service', 'maintenance'],
    ['accidents', 'Accidents', 'accidents'],
    ['trips', 'Trips', 'trips'],
    ['fuel', 'Fuel', 'fuel'],
    ['subtype', 'Type specs', 'subtype']
  ];
  elements.detail.innerHTML = `<div class="detail-content">
    <div class="detail-topline"><span class="detail-index">VEHICLE ${escapeHtml(vehicle.VEHICLE_ID)}</span><span class="registration">${escapeHtml(vehicle.REGISTRATION_NO)}</span></div>
    <h3 class="detail-title">${escapeHtml(vehicle.MODEL)}</h3>
    <div class="detail-subtitle">${escapeHtml(vehicle.BRAND)} · ${escapeHtml(vehicle.VEHICLE_TYPE)}</div>
    <div class="detail-facts">
      <div class="detail-fact"><span>STATUS</span><strong>${escapeHtml(vehicle.STATUS || 'Unknown')}</strong></div>
      <div class="detail-fact"><span>YEAR</span><strong>${escapeHtml(vehicle.MANUFACTURING_YEAR || 'N/A')}</strong></div>
      <div class="detail-fact"><span>COLOR</span><strong>${escapeHtml(vehicle.COLOR || 'N/A')}</strong></div>
      <div class="detail-fact"><span>FUEL ENTRIES</span><strong>${number((data.fuel || []).length)}</strong></div>
    </div>
    <div class="detail-tabs" role="tablist">${sections.map((section, index) => `<button type="button" class="detail-tab${index === 0 ? ' active' : ''}" role="tab" data-section="${section[0]}">${section[1]}</button>`).join('')}</div>
    <div class="detail-table-link-row"><a class="panel-link" id="open-related-table" href="#">Open related table <span aria-hidden="true">&#8594;</span></a></div>
    <div class="record-list" id="record-list"></div>
    <section class="procedure-report" aria-label="PL/SQL procedure output">
      <div class="procedure-report-heading"><span>ORACLE PL/SQL REPORT</span><span class="report-status">AUTO</span></div>
      <div id="procedure-report"><div class="report-loading">Preparing procedure reports...</div></div>
    </section>
  </div>`;
  const tabButtons = elements.detail.querySelectorAll('.detail-tab');
  const openTableLink = elements.detail.querySelector('#open-related-table');
  const vehicleId = data.vehicle.VEHICLE_ID;
  const tableForSection = {
    ownership: 'vehicle-ownership', insurance: 'insurance', warranty: 'warranty',
    maintenance: 'maintenance', accidents: 'accident', trips: 'trip', fuel: 'fuel-record'
  };
  const subtypeTable = String(data.vehicle.VEHICLE_TYPE || '').toLowerCase();
  if (['bike', 'car', 'truck', 'bus'].includes(subtypeTable)) tableForSection.subtype = subtypeTable;
  const selectSection = section => {
    if (openTableLink) {
      const tableSlug = tableForSection[section];
      openTableLink.href = tableSlug ? `/${tableSlug}.html?vehicle_id=${encodeURIComponent(vehicleId)}` : '#';
      openTableLink.hidden = !tableSlug;
    }
    renderRecords(section, data[section] || []);
  };
  tabButtons.forEach(button => button.addEventListener('click', () => {
    tabButtons.forEach(tab => tab.classList.toggle('active', tab === button));
    selectSection(button.dataset.section);
  }));
  selectSection(sections[0][0]);
}

function renderRecords(section, records) {
  const list = document.querySelector('#record-list');
  if (!records.length) {
    list.innerHTML = '<div class="record-none">No records for this vehicle.</div>';
    return;
  }
  list.innerHTML = records.map(record => {
    const [title, meta] = recordText(section, record);
    return `<article class="record-item"><div class="record-title">${escapeHtml(title)}</div><div class="record-meta">${escapeHtml(meta)}</div></article>`;
  }).join('');
}

function recordText(section, row) {
  switch (section) {
    case 'ownership': return [`${row.OWNER_NAME || `Customer ${row.CUSTOMER_ID}`}`, `${dateOnly(row.START_DATE)} → ${dateOnly(row.END_DATE)}`];
    case 'insurance': return [`${row.INSURANCE_COMPANY || 'Insurance'} · ${row.POLICY_NO || 'No policy number'}`, `Expires ${dateOnly(row.EXPIRY_DATE)} · Premium ${money(row.PREMIUM)}`];
    case 'warranty': return [`${row.WARRANTY_PROVIDER || 'Warranty'} · ${row.WARRANTY_TYPE || ''}`, `Expires ${dateOnly(row.EXPIRY_DATE)} · ${row.WARRANTY_STATUS || 'Status unavailable'} · ${row.COVERAGE_DETAILS || ''}`];
    case 'maintenance': return [`${row.SERVICE_TYPE || 'Service'} · ${dateOnly(row.SERVICE_DATE)}`, `${row.CENTER_NAME || 'Service center N/A'} · ${row.DESCRIPTION || ''} · ${money(row.COST)}${row.PAYMENT_STATUS ? ` · ${row.PAYMENT_STATUS}` : ''}`];
    case 'accidents': return [`${dateOnly(row.ACCIDENT_DATE)} · ${row.LOCATION || 'Location N/A'}`, `${row.DESCRIPTION || 'No description'} · Damage ${money(row.DAMAGE_COST)} · ${row.DRIVER_NAME || 'Driver N/A'}`];
    case 'trips': return [`${row.START_LOCATION || 'Start N/A'} → ${row.DESTINATION || 'Destination N/A'}`, `${dateTime(row.START_TIME)} · ${row.DRIVER_NAME || 'Driver N/A'} · ${row.DISTANCE == null ? 'Distance N/A' : `${number(row.DISTANCE)} km`}`];
    case 'fuel': return [`${row.FUEL_TYPE || 'Fuel'} · ${dateOnly(row.FUEL_DATE)}`, `${number(row.QUANTITY)} L · ${money(row.AMOUNT)} · Odometer ${number(row.ODOMETER_READING)}`];
    case 'subtype': return Object.entries(row).filter(([key]) => key !== 'VEHICLE_ID').map(([key, value]) => `${label(key)}: ${value == null ? 'N/A' : value}`).slice(0, 2).join(' · ') ? [sectionName(row), Object.entries(row).filter(([key]) => key !== 'VEHICLE_ID').map(([key, value]) => `${label(key)}: ${value == null ? 'N/A' : value}`).join(' · ')] : ['Type specifications', 'No subtype attributes'];
    default: return ['Record', ''];
  }
}

function sectionName(row) {
  return row.BIKE_TYPE || row.CAR_TYPE || row.TRUCK_TYPE || row.BUS_TYPE || 'Type specifications';
}

function renderTypes(types, total) {
  const container = document.querySelector('#type-list');
  if (!container) return;
  if (!types.length) {
    container.innerHTML = '<div class="type-empty">No vehicle types found.</div>';
    return;
  }
  container.innerHTML = types.map(item => {
    const percent = total ? Math.max(3, Math.round(Number(item.COUNT) / total * 100)) : 0;
    return `<div class="type-item"><span>${escapeHtml(item.NAME)}</span><strong>${number(item.COUNT)}</strong><div class="type-track"><i style="width:${percent}%"></i></div></div>`;
  }).join('');
}

function emptyDetail(message) {
  return `<div class="detail-empty"><div class="detail-index">VEHICLE DETAILS</div><div class="detail-empty-mark">R</div><h3>Vehicle details</h3><p>${escapeHtml(message)}</p></div>`;
}

function statusClass(status) {
  if (status === 'Active') return 'status-active';
  if (status === 'Under Maintenance') return 'status-maintenance';
  if (status === 'Accident Repair') return 'status-accident';
  return '';
}

function dateOnly(value) {
  if (!value) return 'Date N/A';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? String(value).slice(0, 10) : new Intl.DateTimeFormat('en', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(parsed);
}

function dateTime(value) {
  if (!value) return 'Time N/A';
  return String(value).replace('T', ' ').slice(0, 16);
}

function money(value) {
  if (value == null || value === '') return 'Amount N/A';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(Number(value));
}

function number(value) {
  return value == null || value === '' ? '—' : new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(Number(value));
}

function label(value) {
  return value.toLowerCase().split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
}

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[character]));
}

function showToast(message) {
  if (!elements.toast) return;
  elements.toast.textContent = message;
  elements.toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => elements.toast.classList.remove('show'), 3000);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    injectSidebarTableLinks();
    configureSingleTablePage();
    refreshAll();
  });
} else {
  injectSidebarTableLinks();
  configureSingleTablePage();
  refreshAll();
}

async function loadOverviewPanels() {
  const needsVehicles = !!elements.overviewStatus;
  const needsMaintenance = !!elements.overviewMaintenance;
  const needsFuel = !!elements.fuelLatest || !!document.querySelector('#fuel-volume') || !!document.querySelector('#fuel-spend');
  const jobs = [];
  if (needsVehicles) jobs.push(request('/api/tables/VEHICLE'));
  if (needsMaintenance) jobs.push(request('/api/tables/MAINTENANCE'));
  if (needsFuel) jobs.push(request('/api/tables/FUEL_RECORD'));

  try {
    const results = await Promise.all(jobs);
    let resultIndex = 0;
    if (needsVehicles) renderOverviewStatus((results[resultIndex++].rows || []));
    if (needsMaintenance) renderOverviewMaintenance((results[resultIndex++].rows || []));
    if (needsFuel) renderFuelSnapshot((results[resultIndex++].rows || []));
  } catch (error) {
    [elements.overviewStatus, elements.overviewMaintenance, elements.fuelLatest].filter(Boolean).forEach(container => {
      container.innerHTML = `<div class="panel-error">${escapeHtml(error.message)}</div>`;
    });
  }
}

function renderOverviewStatus(rows) {
  if (!elements.overviewStatus) return;
  const counts = rows.reduce((result, row) => {
    const status = String(row.STATUS || 'Unknown');
    result[status] = (result[status] || 0) + 1;
    return result;
  }, {});
  const statuses = ['Active', 'Under Maintenance', 'Accident Repair', 'Inactive'];
  const entries = statuses.filter(status => counts[status]).map(status => [status, counts[status]]);
  Object.keys(counts).filter(status => !statuses.includes(status)).forEach(status => entries.push([status, counts[status]]));
  if (!entries.length) {
    elements.overviewStatus.innerHTML = '<div class="panel-empty">No vehicle records found.</div>';
    return;
  }
  const total = rows.length || 1;
  elements.overviewStatus.innerHTML = entries.map(([status, count]) => {
    const proportion = Math.round(count / total * 100);
    return `<div class="status-row"><div class="status-row-label"><span class="status-label ${statusClass(status)}">${escapeHtml(status)}</span><strong>${number(count)}</strong></div><div class="status-track"><i style="width:${proportion}%"></i></div></div>`;
  }).join('');
}

function renderOverviewMaintenance(rows) {
  if (!elements.overviewMaintenance) return;
  const sorted = rows.slice().sort((left, right) => String(right.SERVICE_DATE || '').localeCompare(String(left.SERVICE_DATE || ''))).slice(0, 5);
  if (!sorted.length) {
    elements.overviewMaintenance.innerHTML = '<div class="panel-empty">No maintenance records found.</div>';
    return;
  }
  elements.overviewMaintenance.innerHTML = sorted.map(record => `<a class="maintenance-row" href="/maintenance.html"><span class="maintenance-date">${escapeHtml(dateOnly(record.SERVICE_DATE))}</span><span class="maintenance-main"><strong>${escapeHtml(record.SERVICE_TYPE || 'Service')} · Vehicle ${escapeHtml(record.VEHICLE_ID)}</strong><small>${escapeHtml(record.DESCRIPTION || 'No service notes')}</small></span><span class="maintenance-cost">${money(record.COST)}</span></a>`).join('');
}

function renderFuelSnapshot(rows) {
  const volume = rows.reduce((total, row) => total + (Number(row.QUANTITY) || 0), 0);
  const spend = rows.reduce((total, row) => total + (Number(row.AMOUNT) || 0), 0);
  const volumeElement = document.querySelector('#fuel-volume');
  const spendElement = document.querySelector('#fuel-spend');
  if (volumeElement) volumeElement.textContent = number(volume);
  if (spendElement) spendElement.textContent = money(spend);
  if (!elements.fuelLatest) return;
  const latest = rows.slice().sort((left, right) => String(right.FUEL_DATE || '').localeCompare(String(left.FUEL_DATE || '')))[0];
  if (!latest) {
    elements.fuelLatest.textContent = 'No fuel records found.';
    return;
  }
  elements.fuelLatest.textContent = `${dateOnly(latest.FUEL_DATE)} · Vehicle ${latest.VEHICLE_ID} · ${number(latest.QUANTITY)} L ${latest.FUEL_TYPE || 'fuel'}`;
}