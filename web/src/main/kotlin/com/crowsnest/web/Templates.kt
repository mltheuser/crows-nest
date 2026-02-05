package com.crowsnest.web

import kotlinx.html.*

fun HTML.landingPage() {
    head {
        title { +"CrowsNest - Find Your Next Opportunity" }
        style { unsafe { raw(CSS) } }
    }
    body {
        div("container") {
            div("split-layout") {
                div("hero-text") {
                    h1 { +"Your Lookout for Career Opportunities" }
                    p {
                        +"We use AI agents to scan the web and find the perfect job matches for you."
                        br {}
                        +"Stop searching. Start finding."
                    }
                    br {}
                    a(href = "/search", classes = "btn btn-primary") {
                        +"Find what you're looking for"
                    }
                }
                div("hero-image") {
                    img(
                            src = "/static/crow-mascot.svg",
                            classes = "mascot",
                            alt = "CrowsNest Mascot"
                    )
                }
            }
        }
    }
}

fun HTML.searchPage() {
    head {
        title { +"Tell us about yourself - CrowsNest" }
        script(src = "https://unpkg.com/htmx.org@1.9.10") {}
        style { unsafe { raw(CSS) } }
    }
    body {
        div("container") {
            div("full-height") {
                br {}
                a(href = "/", classes = "btn btn-outline") { +"← Back" }
                br {}

                h1 { +"Let's find your opportunity" }
                p { +"Tell us what you're looking for, and our AI will scour the web for you." }
                br {}

                form {
                    attributes["hx-post"] = "/search"
                    attributes["hx-swap"] = "outerHTML"

                    div("glass-card") {
                        div("form-group") {
                            label {
                                htmlFor = "whatISearch"
                                +"1. What are you looking for?"
                            }
                            textArea {
                                id = "whatISearch"
                                name = "whatISearch"
                                required = true
                                placeholder =
                                        "I'm looking for a senior Kotlin developer role, remote-friendly, in a startup environment..."
                                rows = "4"
                            }
                            div("help-text") {
                                +"💡 Be specific about role, location, industry, and company size."
                            }
                        }

                        div("form-group") {
                            label {
                                htmlFor = "aboutMe"
                                +"2. Tell us about your experience"
                            }
                            textArea {
                                id = "aboutMe"
                                name = "aboutMe"
                                required = true
                                placeholder =
                                        "I have 5 years of experience with Kotlin and JVM technologies. I've worked at..."
                                rows = "4"
                            }
                            div("help-text") {
                                +"💡 Paste your bio or summary here. We'll use this to match you with requirements."
                            }
                        }

                        button(type = ButtonType.submit, classes = "btn btn-primary") {
                            +"Continue"
                        }
                    }
                }
            }
        }
    }
}

fun HTML.authPage(error: String? = null, googleEnabled: Boolean = false) {
    head {
        title { +"Sign in - CrowsNest" }
        script(src = "https://unpkg.com/htmx.org@1.9.10") {}
        style { unsafe { raw(CSS) } }
    }
    body {
        div("auth-container") {
            div("auth-box glass-card") {
                a(href = "/search", classes = "btn btn-outline") { +"← Back" }
                br {}
                br {}

                h2 { +"Save your search" }
                p {
                    +"Create an account to save your search and get notified when we find matches."
                }
                br {}

                if (error != null) {
                    div("error") { +error }
                    br {}
                }

                if (googleEnabled) {
                    a(href = "/auth/google", classes = "btn btn-outline") {
                        +"Continue with Google"
                    }
                    div("divider") { span { +"or" } }
                }

                div("auth-tabs") {
                    button(classes = "btn btn-outline tab active") {
                        attributes["onclick"] = "showTab('signup')"
                        +"Sign up"
                    }
                    button(classes = "btn btn-outline tab") {
                        attributes["onclick"] = "showTab('login')"
                        +"Log in"
                    }
                }
                br {}

                form(classes = "auth-form") {
                    id = "signup-form"
                    action = "/auth/signup"
                    method = FormMethod.post

                    div("form-group") {
                        label { +"Email" }
                        input(type = InputType.email, name = "email") {
                            required = true
                            placeholder = "you@example.com"
                        }
                    }
                    div("form-group") {
                        label { +"Password" }
                        input(type = InputType.password, name = "password") {
                            required = true
                            placeholder = "6+ characters"
                            minLength = "6"
                        }
                    }
                    button(type = ButtonType.submit, classes = "btn btn-primary") { +"Sign up" }
                }

                form(classes = "auth-form hidden") {
                    id = "login-form"
                    action = "/auth/login"
                    method = FormMethod.post

                    div("form-group") {
                        label { +"Email" }
                        input(type = InputType.email, name = "email") {
                            required = true
                            placeholder = "you@example.com"
                        }
                    }
                    div("form-group") {
                        label { +"Password" }
                        input(type = InputType.password, name = "password") {
                            required = true
                            placeholder = "Your password"
                        }
                    }
                    button(type = ButtonType.submit, classes = "btn btn-primary") { +"Log in" }
                }

                script {
                    unsafe {
                        raw(
                                """
                        function showTab(tab) {
                            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active', 'btn-primary'));
                            document.querySelectorAll('.tab').forEach(t => t.classList.add('btn-outline'));
                            event.target.classList.remove('btn-outline');
                            event.target.classList.add('active', 'btn-primary');
                            document.getElementById('signup-form').classList.toggle('hidden', tab !== 'signup');
                            document.getElementById('login-form').classList.toggle('hidden', tab !== 'login');
                        }
                    """.trimIndent()
                        )
                    }
                }
            }
        }
    }
}

fun HTML.dashboardPage(showSavedMessage: Boolean) {
    head {
        title { +"Dashboard - CrowsNest" }
        style { unsafe { raw(CSS) } }
    }
    body {
        div("container") {
            div("dashboard-header") {
                h1 { +"CrowsNest Dashboard" }
                form {
                    attributes["action"] = "/logout"
                    attributes["method"] = "post"
                    button(type = ButtonType.submit, classes = "btn btn-outline") { +"Logout" }
                }
            }

            if (showSavedMessage) {
                div("glass-card") {
                    style = "border-color: var(--sea-foam); background: rgba(16, 185, 129, 0.1);"
                    +"✅ Your search has been saved! We're now scanning for matches."
                }
                br {}
            }

            div("split-layout") {
                // Left Column: Active Searches
                div("section") {
                    h2 { +"Your Searches" }
                    div("list-group") {
                        // Mock Search Item
                        div("list-item") {
                            div {
                                h3 {
                                    style = "font-size: 1.1rem; margin-bottom: 4px;"
                                    +"🔍 Kotlin Developer"
                                }
                                p {
                                    style = "font-size: 0.9rem;"
                                    +"Remote • Startup • Senior"
                                }
                            }
                            span(classes = "badge") { +"Active" }
                        }
                    }
                    br {}
                    a(href = "/search", classes = "btn btn-outline") { +"+ Add new search" }
                }

                // Right Column: Matched Offers
                div("section") {
                    h2 { +"Matched Opportunities" }

                    // Mock Matches
                    div("list-group") {
                        div("list-item") {
                            div {
                                h3 {
                                    style = "font-size: 1.1rem; margin-bottom: 4px;"
                                    +"Senior Kotlin Developer"
                                }
                                p {
                                    style = "font-size: 0.9rem;"
                                    +"JetBrains • Remote"
                                }
                                div("match-bar-container") {
                                    div("match-bar-fill") { style = "width: 92%" }
                                }
                            }
                            div {
                                style = "text-align: right;"
                                span(classes = "badge") {
                                    style = "color: var(--sea-foam);"
                                    +"92%"
                                }
                                br {}
                                br {}
                                a(href = "#", classes = "btn btn-primary") {
                                    style = "padding: 6px 12px; font-size: 0.8rem;"
                                    +"View"
                                }
                            }
                        }

                        div("list-item") {
                            div {
                                h3 {
                                    style = "font-size: 1.1rem; margin-bottom: 4px;"
                                    +"Backend Engineer"
                                }
                                p {
                                    style = "font-size: 0.9rem;"
                                    +"Netflix • Los Gatos"
                                }
                                div("match-bar-container") {
                                    div("match-bar-fill") { style = "width: 85%" }
                                }
                            }
                            div {
                                style = "text-align: right;"
                                span(classes = "badge") {
                                    style = "color: var(--sunset-gold);"
                                    +"85%"
                                }
                                br {}
                                br {}
                                a(href = "#", classes = "btn btn-primary") {
                                    style = "padding: 6px 12px; font-size: 0.8rem;"
                                    +"View"
                                }
                            }
                        }
                    }

                    br {}
                    div {
                        style = "text-align: center; opacity: 0.5;"
                        img(src = "/static/crow-mascot.svg", classes = "mascot-small")
                        p { +"Scanning for more matches..." }
                    }
                }
            }
        }
    }
}

const val CSS =
        """
:root {
    --deep-ocean: #0f1419;
    --night-sky: #1a2332;
    --moonlight: #e7edf4;
    --silver-mist: #8899a6;
    --crow-purple: #7c3aed;
    --sunset-gold: #f59e0b;
    --sea-foam: #10b981;
    --storm-red: #ef4444;
    --glass-bg: rgba(255, 255, 255, 0.03);
    --glass-border: rgba(255, 255, 255, 0.08);
}

* { box-sizing: border-box; margin: 0; padding: 0; }

body { 
    font-family: 'Inter', system-ui, -apple-system, sans-serif;
    background-color: var(--deep-ocean);
    color: var(--moonlight);
    min-height: 100vh;
    line-height: 1.6;
}

/* Layout Utilities */
.container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 24px;
}

.full-height {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
}

.split-layout {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 48px;
    align-items: center;
    min-height: 100vh;
}

@media (max-width: 768px) {
    .split-layout { grid-template-columns: 1fr; text-align: center; padding: 48px 0; }
}

/* Typography */
h1, h2, h3 { color: #fff; font-weight: 700; letter-spacing: -0.02em; }
h1 { font-size: 3.5rem; line-height: 1.1; margin-bottom: 24px; }
h2 { font-size: 1.8rem; margin-bottom: 16px; }
p { color: var(--silver-mist); font-size: 1.1rem; }

/* Components */
.btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 12px 24px;
    border-radius: 8px;
    font-weight: 600;
    text-decoration: none;
    transition: all 0.2s;
    cursor: pointer;
    border: none;
    font-size: 1rem;
}

.btn-primary {
    background: var(--crow-purple);
    color: white;
}
.btn-primary:hover { background: #6d28d9; transform: translateY(-1px); }

.btn-outline {
    background: transparent;
    border: 1px solid var(--glass-border);
    color: var(--silver-mist);
}
.btn-outline:hover { border-color: var(--moonlight); color: var(--moonlight); }

.glass-card {
    background: var(--glass-bg);
    border: 1px solid var(--glass-border);
    backdrop-filter: blur(12px);
    border-radius: 16px;
    padding: 32px;
}

/* Form Styles */
.form-group { margin-bottom: 24px; }
label { display: block; margin-bottom: 8px; font-weight: 500; color: var(--moonlight); }
.help-text { font-size: 0.9rem; color: var(--silver-mist); margin-top: 6px; }

input, textarea {
    width: 100%;
    padding: 16px;
    background: var(--night-sky);
    border: 1px solid var(--glass-border);
    border-radius: 8px;
    color: white;
    font-size: 1rem;
    font-family: inherit;
    transition: border-color 0.2s;
}
input:focus, textarea:focus { outline: none; border-color: var(--crow-purple); }

/* List View Styles */
.list-group { display: flex; flex-direction: column; gap: 12px; }

.list-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px;
    background: var(--glass-bg);
    border: 1px solid var(--glass-border);
    border-radius: 12px;
    transition: transform 0.2s, background 0.2s;
}
.list-item:hover { background: rgba(255,255,255,0.05); transform: translateX(4px); }

.match-bar-container {
    width: 120px;
    height: 8px;
    background: rgba(255,255,255,0.1);
    border-radius: 4px;
    overflow: hidden;
    margin-top: 4px;
}
.match-bar-fill {
    height: 100%;
    background: linear-gradient(90deg, var(--sea-foam), var(--sunset-gold));
    border-radius: 4px;
}

.badge {
    font-size: 0.85rem;
    font-weight: 600;
    padding: 4px 8px;
    border-radius: 4px;
    background: rgba(255,255,255,0.1);
    color: var(--moonlight);
}

/* Illustrations */
.mascot { width: 100%; max-width: 400px; height: auto; }
.mascot-small { width: 120px; margin-bottom: 24px; opacity: 0.8; }

/* Auth Layout */
.auth-container {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
}
.auth-box { width: 100%; max-width: 440px; }

/* Dashboard Layout */
.dashboard-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 32px 0;
    margin-bottom: 24px;
    border-bottom: 1px solid var(--glass-border);
}
"""
