const JOBS = {
  "Java Developer": ["Core Java & OOP", "Collections & Generics", "Multithreading", "Exception handling & I/O", "SQL & JDBC", "Data Structures & Algorithms", "Git & GitHub", "Maven/Gradle", "Spring Boot", "REST APIs", "Hibernate/JPA", "JUnit testing", "Microservices basics", "Docker basics"],
  "Python Developer": ["Python fundamentals", "OOP in Python", "Data Structures & Algorithms", "SQL", "Git & GitHub", "Flask/Django", "REST APIs", "Testing (pytest)", "Docker basics"],
  "Web Developer": ["HTML & CSS", "JavaScript (ES6+)", "Responsive design", "Git & GitHub", "React or similar", "Node.js basics", "REST APIs", "SQL/NoSQL basics", "Deployment basics"],
  "Data Analyst": ["Excel", "SQL", "Python (pandas)", "Statistics", "Data visualization", "Power BI/Tableau", "Data cleaning", "Communication"],
  "Full Stack Developer": ["HTML, CSS, JavaScript", "React or similar", "Node.js/Express or Spring Boot", "REST APIs", "SQL & NoSQL", "Git & GitHub", "Authentication", "Docker basics", "Deployment basics"]
};

const LOCAL_API_BASE = `${window.location.protocol}//${window.location.hostname}:8081`;
const API_BASE = (import.meta.env?.VITE_API_BASE_URL
  ?? (import.meta.env?.PROD ? "" : LOCAL_API_BASE)).replace(/\/$/, "");
const API_REQUEST_TIMEOUT_MS = 90_000;
const byId = (id) => document.getElementById(id);
const sections = [byId("s1"), byId("s2"), byId("s3")];
const state = { account: null, profile: {}, skillsByJob: {} };
let csrfToken = "";
let isRegisterMode = false;
let saveTimer;
let csrfPromise = null;
const resetToken = new URLSearchParams(window.location.search).get("resetToken");

function setButtonLoading(button, text) {
  if (!button) return;
  button.disabled = true;
  button.dataset.originalText = button.textContent;
  button.classList.add("is-loading");
  button.innerHTML = `<span class="button-spinner" aria-hidden="true"></span><span>${text}</span>`;
}

function resetButtonLoading(button, fallbackText) {
  if (!button) return;
  button.disabled = false;
  button.classList.remove("is-loading");
  button.textContent = button.dataset.originalText || fallbackText || "Submit";
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), API_REQUEST_TIMEOUT_MS);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new Error("The account service did not respond within 90 seconds. It may be waking up; please try again.");
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

function refreshCsrfToken() {
  if (csrfPromise) return csrfPromise;
  csrfPromise = (async () => {
    const response = await fetchWithTimeout(`${API_BASE}/api/auth/csrf`, { credentials: "include" });
    if (!response.ok) {
      throw new Error(`Could not establish a secure session (${response.status}). Please try again.`);
    }
    const data = await response.json();
    csrfToken = data?.token || "";
    if (!csrfToken) {
      throw new Error("The account service did not provide a secure session token. Please try again.");
    }
    return csrfToken;
  })().finally(() => {
    csrfPromise = null;
  });
  return csrfPromise;
}

async function ensureCsrfToken() {
  if (csrfToken) return csrfToken;
  return refreshCsrfToken();
}

async function api(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  if (options.body !== undefined) headers.set("Content-Type", "application/json");

  if (!["GET", "HEAD", "OPTIONS"].includes(method) && !path.includes("/api/auth/csrf")) {
    if (!csrfToken) {
      await ensureCsrfToken();
    }
    if (csrfToken) {
      headers.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  const response = await fetchWithTimeout(`${API_BASE}${path}`, {
    ...options,
    method,
    headers,
    credentials: "include",
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (response.status === 403 && !options._csrfRetry && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    await refreshCsrfToken();
    return api(path, { ...options, _csrfRetry: true });
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get("content-type") || "";
  const result = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const error = new Error(result?.message || result?.detail || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return result;
}

function showSection(activeSection) {
  sections.forEach((section) => { section.hidden = section !== activeSection; });
  activeSection.scrollIntoView({ behavior: "smooth", block: "start" });
}

function setAuthMode(register) {
  isRegisterMode = register;
  byId("auth-form").hidden = false;
  byId("forgot-form").hidden = true;
  byId("reset-form").hidden = true;
  byId("auth-title").textContent = register ? "Create your account." : "Welcome back.";
  byId("auth-description").textContent = register
    ? "Create an account to keep your profile and skill progress available on any device."
    : "Sign in to see your saved profile and skills from any device.";
  byId("auth-submit").textContent = register ? "Create account" : "Sign in";
  byId("auth-mode").textContent = register ? "Already have an account? Sign in" : "Create account";
  byId("forgot-password").hidden = register;
  byId("auth-password").autocomplete = register ? "new-password" : "current-password";
  byId("auth-error").textContent = "";
}

function showForgotPassword() {
  byId("auth-form").hidden = true;
  byId("forgot-form").hidden = false;
  byId("reset-form").hidden = true;
  byId("auth-title").textContent = "Reset your password.";
  byId("auth-description").textContent = "Enter your email and we'll send a one-time verification code if an account exists.";
  byId("forgot-email").value = byId("auth-email").value.trim();
  byId("forgot-error").textContent = "";
  byId("forgot-status").textContent = "";
  byId("forgot-email").focus();
}

function showResetPassword() {
  byId("auth-form").hidden = true;
  byId("forgot-form").hidden = true;
  byId("reset-form").hidden = false;
  byId("auth-title").textContent = "Choose a new password.";
  byId("auth-description").textContent = "Enter the 6-digit code from your email and choose a new password.";
  byId("reset-error").textContent = "";
  if (resetToken) {
    byId("reset-token").value = resetToken;
    byId("reset-password").focus();
  } else {
    byId("reset-token").focus();
  }
}

function selectedSkills(job) {
  const saved = state.skillsByJob[job];
  return Array.isArray(saved) ? saved : [];
}

function renderSkillList(job) {
  const skills = JOBS[job];
  const selected = new Set(selectedSkills(job));
  const list = byId("list");
  list.replaceChildren();

  skills.forEach((skill, index) => {
    const label = document.createElement("label");
    label.className = "skill-row";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.id = `skill-${index}`;
    checkbox.value = skill;
    checkbox.checked = selected.has(skill);

    const text = document.createElement("span");
    text.textContent = skill;
    label.htmlFor = checkbox.id;
    label.append(checkbox, text);
    list.append(label);
  });

  byId("s2sub").textContent = `Select the skills you already have for a ${job} role. They are arranged in a suggested learning order.`;
  byId("skill-count").textContent = `${skills.length} skills to review`;
}

function captureProfile() {
  state.profile = Object.fromEntries(
    ["name", "edu", "branch", "year", "exp", "job"].map((field) => [field, byId(field).value.trim()])
  );
  return {
    name: state.profile.name,
    education: state.profile.edu,
    branch: state.profile.branch,
    year: state.profile.year,
    experience: state.profile.exp,
    job: state.profile.job,
    skillsByJob: state.skillsByJob
  };
}

async function saveProfile() {
  if (!state.account || !state.profile.job) return;
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = undefined;
  }
  byId("save-status").textContent = "Saving…";
  byId("skills-save-status").textContent = "Saving…";
  try {
    await api("/api/account/profile", { method: "PUT", body: captureProfile() });
    byId("save-status").textContent = "Saved to your account.";
    byId("skills-save-status").textContent = "Saved to your account.";
    byId("err").textContent = "";
    byId("skills-error").textContent = "";
  } catch (error) {
    byId("save-status").textContent = "";
    byId("skills-save-status").textContent = "";
    const message = `Your changes were not saved. ${error.message}`;
    byId("err").textContent = message;
    byId("skills-error").textContent = message;
    throw error;
  }
}

function scheduleProfileSave() {
  if (!state.account || !state.profile.job) return;
  if (saveTimer) clearTimeout(saveTimer);
  byId("save-status").textContent = "Saving…";
  byId("skills-save-status").textContent = "Saving…";
  saveTimer = setTimeout(() => {
    saveTimer = undefined;
    saveProfile().catch(() => {});
  }, 300);
}

function saveSelectedSkills() {
  const job = byId("job").value;
  state.skillsByJob[job] = [...byId("list").querySelectorAll("input:checked")].map((input) => input.value);
  scheduleProfileSave();
}

function appendItems(list, items, emptyMessage) {
  list.replaceChildren();
  (items.length ? items : [emptyMessage]).forEach((item) => {
    const li = document.createElement("li");
    li.textContent = item;
    list.append(li);
  });
}

function showResults() {
  const job = byId("job").value;
  const skills = JOBS[job];
  if (!skills || !skills.length) return;
  const selected = new Set(selectedSkills(job));
  const have = skills.filter((skill) => selected.has(skill));
  const gaps = skills.filter((skill) => !selected.has(skill));
  const percentage = Math.round((have.length / skills.length) * 100);

  byId("results-title").textContent = `${byId("name").value.trim()}, your ${job} snapshot`;
  byId("readiness-value").textContent = `${percentage}%`;
  byId("score").textContent = `${have.length} of ${skills.length} skills covered · ${byId("year").value} · ${byId("edu").value} ${byId("branch").value}`;
  appendItems(byId("have"), have, "Start with one skill and build from there.");
  appendItems(byId("gaps"), gaps, "No gaps in this checklist. Put your skills to work in a project.");
  const readinessRing = byId("readiness-ring");
  readinessRing.style.setProperty("--progress", `${percentage}%`);
  readinessRing.setAttribute("aria-valuenow", String(percentage));
  showSection(byId("s3"));
}

function populateRoles() {
  const jobSelect = byId("job");
  Object.keys(JOBS).forEach((job) => {
    const option = document.createElement("option");
    option.value = job;
    option.textContent = job;
    jobSelect.append(option);
  });
}

function showManager(account, profile) {
  state.account = account;
  state.profile = profile ? {
    name: profile.name,
    edu: profile.education,
    branch: profile.branch,
    year: profile.year,
    exp: profile.experience,
    job: profile.job
  } : {};
  state.skillsByJob = profile?.skillsByJob || {};

  for (const field of ["name", "edu", "branch", "year", "exp", "job"]) {
    byId(field).value = state.profile[field] || "";
  }
  byId("auth-screen").hidden = true;
  byId("manager").hidden = false;
  document.querySelector(".account-bar").hidden = false;
  byId("account-email").textContent = account.email;
  byId("err").textContent = "";
  byId("save-status").textContent = profile ? "Your saved profile is ready." : "";
  byId("skills-save-status").textContent = "";
  showSection(byId("s1"));
}

async function loadAccount(account = null) {
  if (!account) {
    await ensureCsrfToken();
    account = await api("/api/auth/me");
  }
  const profile = await api("/api/account/profile").catch(() => null);
  showManager(account, profile);
}

populateRoles();
setAuthMode(false);

byId("auth-mode").addEventListener("click", () => setAuthMode(!isRegisterMode));
byId("forgot-password").addEventListener("click", showForgotPassword);
byId("forgot-back").addEventListener("click", () => setAuthMode(false));
byId("reset-back").addEventListener("click", () => setAuthMode(false));
byId("auth-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!byId("auth-form").reportValidity()) return;

  const submit = byId("auth-submit");
  const actionText = isRegisterMode ? "Creating account..." : "Signing in...";
  setButtonLoading(submit, actionText);
  byId("auth-error").textContent = "";
  byId("auth-error").className = "error-message";

  const warmTimer = setTimeout(() => {
    byId("auth-error").className = "save-status server-waking-note";
    byId("auth-error").textContent = "Connecting to server... (Render free service may take 30–50s to wake up if idle)";
  }, 2500);

  try {
    const credentials = {
      email: byId("auth-email").value.trim(),
      password: byId("auth-password").value
    };
    if (isRegisterMode) await api("/api/auth/register", { method: "POST", body: credentials });
    const account = await api("/api/auth/login", { method: "POST", body: credentials });
    byId("auth-password").value = "";
    clearTimeout(warmTimer);
    byId("auth-error").className = "error-message";
    byId("auth-error").textContent = "";
    await loadAccount(account);
  } catch (error) {
    clearTimeout(warmTimer);
    byId("auth-error").className = "error-message";
    if (error.status === 409) {
      byId("auth-error").textContent = "An account already exists for this email. Sign in instead.";
    } else if (error.status === 401) {
      byId("auth-error").textContent = "Email or password is incorrect.";
    } else if (error instanceof TypeError) {
      byId("auth-error").textContent = "Could not reach the service. Check your connection and try again.";
    } else {
      byId("auth-error").textContent = error.message;
    }
  } finally {
    clearTimeout(warmTimer);
    resetButtonLoading(submit, isRegisterMode ? "Create account" : "Sign in");
  }
});

byId("forgot-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!byId("forgot-form").reportValidity()) return;
  const submit = byId("forgot-submit");
  setButtonLoading(submit, "Sending OTP...");
  byId("forgot-error").textContent = "";
  byId("forgot-status").textContent = "";
  try {
    const result = await api("/api/auth/forgot-password", {
      method: "POST",
      body: { email: byId("forgot-email").value.trim() }
    });
    byId("forgot-status").textContent = result.message;
    if (result?.devOtp) {
      setTimeout(() => {
        showResetPassword();
        byId("reset-token").value = result.devOtp;
        byId("reset-password").focus();
      }, 1500);
    }
  } catch (error) {
    byId("forgot-error").textContent = error instanceof TypeError
        ? "Could not reach the service. Check your connection and try again."
        : error.message;
  } finally {
    resetButtonLoading(submit, "Send OTP");
  }
});

byId("reset-confirm").addEventListener("input", () => {
  byId("reset-confirm").setCustomValidity(
    byId("reset-confirm").value === byId("reset-password").value ? "" : "Passwords do not match."
  );
});
byId("reset-password").addEventListener("input", () => {
  byId("reset-confirm").setCustomValidity(
    byId("reset-confirm").value === byId("reset-password").value ? "" : "Passwords do not match."
  );
});
byId("reset-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const otpCode = byId("reset-token").value.trim();
  if (!otpCode) {
    byId("reset-error").textContent = "Enter the 6-digit verification code from your email.";
    return;
  }
  if (!byId("reset-form").reportValidity()) return;

  const submit = byId("reset-submit");
  setButtonLoading(submit, "Resetting password...");
  byId("reset-error").textContent = "";
  try {
    await api("/api/auth/reset-password", {
      method: "POST",
      body: { token: otpCode, password: byId("reset-password").value }
    });
    byId("reset-token").value = "";
    byId("reset-password").value = "";
    byId("reset-confirm").value = "";
    setAuthMode(false);
    byId("auth-error").textContent = "Your password was reset. Sign in with your new password.";
  } catch (error) {
    byId("reset-error").textContent = error.status === 400
      ? "This verification code is invalid or expired. Request a new OTP."
      : error instanceof TypeError
        ? "Could not reach the service. Check your connection and try again."
        : error.message;
  } finally {
    resetButtonLoading(submit, "Verify code & reset password");
  }
});


byId("profile-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!byId("profile-form").reportValidity()) return;
  byId("err").textContent = "";
  captureProfile();
  try {
    await saveProfile();
    renderSkillList(state.profile.job);
    showSection(byId("s2"));
  } catch {
    // The save error is shown next to the profile form.
  }
});

byId("list").addEventListener("change", saveSelectedSkills);
byId("analyze").addEventListener("click", async () => {
  saveSelectedSkills();
  try {
    await saveProfile();
    showResults();
  } catch {
    byId("skills-error").textContent = "Your changes could not be saved. Please try again.";
  }
});
byId("back1").addEventListener("click", () => showSection(byId("s1")));
byId("back2").addEventListener("click", () => {
  renderSkillList(byId("job").value);
  showSection(byId("s2"));
});
byId("edit-profile").addEventListener("click", () => showSection(byId("s1")));
byId("logout").addEventListener("click", async () => {
  try {
    if (saveTimer) {
      clearTimeout(saveTimer);
      saveTimer = undefined;
      await saveProfile();
    }
    await api("/api/auth/logout", { method: "POST" });
    state.account = null;
    state.profile = {};
    state.skillsByJob = {};
    csrfToken = "";
    document.querySelector(".account-bar").hidden = true;
    byId("manager").hidden = true;
    byId("auth-screen").hidden = false;
    byId("auth-error").textContent = "";
    setAuthMode(false);
  } catch (error) {
    byId("err").textContent = `Sign out failed. ${error.message}`;
  }
});

// Pre-warm backend and initialize CSRF token in the background immediately
ensureCsrfToken().catch(() => {});

if (resetToken) {
  byId("auth-screen").hidden = false;
  showResetPassword();
} else {
  loadAccount().catch((error) => {
    byId("auth-screen").hidden = false;
    if (error.status !== 401) {
      byId("auth-error").textContent = error instanceof TypeError
        ? "Could not reach the service. Check your connection and try again."
        : `Could not connect to the account service. ${error.message}`;
    }
  });
}
