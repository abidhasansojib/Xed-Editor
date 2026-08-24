package com.rk.tabs.markdown

/**
 * Generates a complete, self-contained HTML document with GitHub-Flavored Markdown (GFM)
 * matching GitHub's web browser rendering:
 * - Official GitHub Markdown CSS styling (Light & Dark themes)
 * - GitHub Alerts (> [!NOTE], > [!TIP], > [!IMPORTANT], > [!WARNING], > [!CAUTION]) with official Octicons
 * - Syntax highlighting with language badges and one-click "Copy" buttons
 * - GFM Tables with zebra striping and borders
 * - Task list checkboxes
 * - KaTeX math rendering ($math$ and $$math$$)
 * - Mermaid diagrams (```mermaid)
 * - Collapsible <details><summary> sections
 * - Automatic dark/light theme adaptation matching the app
 */
object GitHubMarkdownTemplate {

    fun generateHtml(markdownContent: String, isDark: Boolean, baseDirUrl: String): String {
        val escapedMarkdown = escapeForJavaScriptTemplate(markdownContent)
        val themeClass = if (isDark) "dark" else "light"
        val bgColor = if (isDark) "#0d1117" else "#ffffff"
        val textColor = if (isDark) "#e6edf3" else "#1f2328"

        return """
<!DOCTYPE html>
<html lang="en" data-color-mode="$themeClass" data-light-theme="light" data-dark-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
    <base href="$baseDirUrl">
    <title>Markdown Preview</title>
    
    <!-- Marked Parser & Highlight.js -->
    <script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/${if (isDark) "github-dark" else "github"}.min.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
    
    <!-- KaTeX Math -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.css">
    <script src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/contrib/auto-render.min.js"></script>

    <!-- Mermaid Diagrams -->
    <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"></script>

    <!-- GitHub Markdown CSS -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.1/github-markdown-${if (isDark) "dark" else "light"}.min.css">

    <style>
        :root {
            color-scheme: ${if (isDark) "dark" else "light"};
        }
        body {
            box-sizing: border-box;
            min-width: 200px;
            max-width: 980px;
            margin: 0 auto;
            padding: 20px 16px 60px 16px;
            background-color: $bgColor;
            color: $textColor;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif;
            font-size: 15px;
            line-height: 1.6;
            word-wrap: break-word;
        }
        .markdown-body {
            background-color: transparent !important;
            color: inherit !important;
            font-family: inherit !important;
        }
        
        /* GitHub Alerts */
        .markdown-alert {
            padding: 0.5rem 1rem;
            margin-bottom: 16px;
            color: inherit;
            border-left: .25em solid;
            border-radius: 6px;
            background-color: ${if (isDark) "rgba(255,255,255,0.03)" else "rgba(0,0,0,0.02)"};
        }
        .markdown-alert-title {
            display: flex;
            font-weight: 600;
            align-items: center;
            line-height: 1;
            margin-bottom: 4px;
            font-size: 14px;
        }
        .markdown-alert-title svg {
            margin-right: 8px;
            vertical-align: text-bottom;
            fill: currentColor;
        }
        .markdown-alert.markdown-alert-note {
            border-left-color: ${if (isDark) "#2f81f7" else "#0969da"};
            background-color: ${if (isDark) "rgba(56, 139, 253, 0.1)" else "#ddf4ff"};
        }
        .markdown-alert.markdown-alert-note .markdown-alert-title {
            color: ${if (isDark) "#2f81f7" else "#0969da"};
        }
        .markdown-alert.markdown-alert-tip {
            border-left-color: ${if (isDark) "#3fb950" else "#1a7f37"};
            background-color: ${if (isDark) "rgba(63, 185, 80, 0.1)" else "#dafbe1"};
        }
        .markdown-alert.markdown-alert-tip .markdown-alert-title {
            color: ${if (isDark) "#3fb950" else "#1a7f37"};
        }
        .markdown-alert.markdown-alert-important {
            border-left-color: ${if (isDark) "#a371f7" else "#8250df"};
            background-color: ${if (isDark) "rgba(163, 113, 247, 0.1)" else "#fbefff"};
        }
        .markdown-alert.markdown-alert-important .markdown-alert-title {
            color: ${if (isDark) "#a371f7" else "#8250df"};
        }
        .markdown-alert.markdown-alert-warning {
            border-left-color: ${if (isDark) "#d29922" else "#9a6700"};
            background-color: ${if (isDark) "rgba(210, 153, 34, 0.1)" else "#fff8c5"};
        }
        .markdown-alert.markdown-alert-warning .markdown-alert-title {
            color: ${if (isDark) "#d29922" else "#9a6700"};
        }
        .markdown-alert.markdown-alert-caution {
            border-left-color: ${if (isDark) "#f85149" else "#cf222e"};
            background-color: ${if (isDark) "rgba(248, 81, 73, 0.1)" else "#ffebe9"};
        }
        .markdown-alert.markdown-alert-caution .markdown-alert-title {
            color: ${if (isDark) "#f85149" else "#cf222e"};
        }

        /* Code Block Copy Button & Header */
        .code-container {
            position: relative;
            margin-bottom: 16px;
        }
        .code-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 6px 12px;
            background-color: ${if (isDark) "#161b22" else "#f6f8fa"};
            border: 1px solid ${if (isDark) "#30363d" else "#d0d7de"};
            border-bottom: none;
            border-top-left-radius: 6px;
            border-top-right-radius: 6px;
            font-size: 12px;
            font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
            font-weight: 600;
            color: ${if (isDark) "#7d8590" else "#57606a"};
        }
        .code-container pre {
            margin-top: 0 !important;
            border-top-left-radius: 0 !important;
            border-top-right-radius: 0 !important;
        }
        .copy-btn {
            background-color: transparent;
            border: 1px solid ${if (isDark) "#30363d" else "#d0d7de"};
            border-radius: 4px;
            color: inherit;
            padding: 3px 8px;
            font-size: 11px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 4px;
            transition: all 0.2s ease;
        }
        .copy-btn:hover {
            background-color: ${if (isDark) "#21262d" else "#eaeef2"};
        }
        .copy-btn.copied {
            color: #3fb950;
            border-color: #3fb950;
        }

        /* Images and media */
        img {
            max-width: 100%;
            height: auto;
            border-radius: 6px;
        }
        
        /* Table enhancements */
        .markdown-body table {
            display: block;
            width: max-content;
            max-width: 100%;
            overflow: auto;
        }
    </style>
</head>
<body class="markdown-body">
    <div id="content"></div>

    <script>
        const OCTICONS = {
            note: '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16"><path d="M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"></path></svg>',
            tip: '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16"><path d="M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 0 1-1.484.211c-.04-.282-.163-.547-.37-.847a8.456 8.456 0 0 0-.542-.68c-.099-.118-.204-.239-.309-.364C3.044 7.604 2.5 6.647 2.5 5.25 2.5 2.457 4.757 0 8 0s5.5 2.457 5.5 5.25c0 1.397-.544 2.354-1.272 3.22-.105.125-.21.246-.309.364a8.458 8.458 0 0 0-.541.68c-.208.3-.33.565-.37.847a.751.751 0 0 1-1.485-.212c.084-.593.337-1.078.621-1.489.203-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637-3.75-4-3.75ZM5.75 12h4.5a.75.75 0 0 1 0 1.5h-4.5a.75.75 0 0 1 0-1.5ZM6.5 15h3a.75.75 0 0 1 0 1.5h-3a.75.75 0 0 1 0-1.5Z"></path></svg>',
            important: '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16"><path d="M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0 1 14.25 13H8.06l-2.573 2.573A1.458 1.458 0 0 1 3 14.543V13H1.75A1.75 1.75 0 0 1 0 11.25Zm1.75-.25a.25.25 0 0 0-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h6.5a.25.25 0 0 0 .25-.25v-9.5a.25.25 0 0 0-.25-.25Zm7 2.25v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 9a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z"></path></svg>',
            warning: '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16"><path d="M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0 1 14.082 15H1.918a1.75 1.75 0 0 1-1.543-2.575Zm1.763.707a.25.25 0 0 0-.44 0L1.698 13.132a.25.25 0 0 0 .22.368h12.164a.25.25 0 0 0 .22-.368Zm.53 3.996v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z"></path></svg>',
            caution: '<svg class="octicon" viewBox="0 0 16 16" width="16" height="16"><path d="M4.47.22A.749.749 0 0 1 5 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 0 1-.22.53l-4.25 4.25A.749.749 0 0 1 11 16H5a.749.749 0 0 1-.53-.22L.22 11.53A.749.749 0 0 1 0 11V5c0-.199.079-.389.22-.53Zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5ZM8 4a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"></path></svg>'
        };

        // Transform GitHub Alerts: > [!NOTE] -> <div class="markdown-alert markdown-alert-note">...</div>
        function transformAlerts(markdown) {
            return markdown.replace(
                /(?:^|\n)> *\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\][^\n]*\n((?:> *[^\n]*\n?)*)/gi,
                function(match, type, content) {
                    const alertType = type.toLowerCase();
                    const cleanContent = content.split('\n').map(line => line.replace(/^> *?/, '')).join('\n');
                    const title = type.charAt(0).toUpperCase() + type.slice(1).toLowerCase();
                    const icon = OCTICONS[alertType] || OCTICONS.note;
                    return `\n<div class="markdown-alert markdown-alert-${'$'}{alertType}"><p class="markdown-alert-title">${'$'}{icon}${'$'}{title}</p>\n\n${'$'}{cleanContent}\n</div>\n`;
                }
            );
        }

        const rawMarkdown = "$escapedMarkdown";

        // Initialize marked with GFM and Highlight.js
        marked.setOptions({
            gfm: true,
            breaks: true,
            highlight: function(code, lang) {
                if (lang && hljs.getLanguage(lang)) {
                    try {
                        return hljs.highlight(code, { language: lang }).value;
                    } catch (err) {}
                }
                return hljs.highlightAuto(code).value;
            }
        });

        // Initialize Mermaid
        mermaid.initialize({
            startOnLoad: false,
            theme: "${if (isDark) "dark" else "default"}",
            securityLevel: 'loose'
        });

        // Render Markdown
        const processedMd = transformAlerts(rawMarkdown);
        const html = marked.parse(processedMd);
        const contentDiv = document.getElementById('content');
        contentDiv.innerHTML = html;

        // Render KaTeX Math
        renderMathInElement(contentDiv, {
            delimiters: [
                {left: '$$', right: '$$', display: true},
                {left: '$', right: '$', display: false},
                {left: '\\(', right: '\\)', display: false},
                {left: '\\[', right: '\\]', display: true}
            ],
            throwOnError: false
        });

        // Wrap code blocks with Header + Copy Button
        document.querySelectorAll('pre code').forEach((block) => {
            const pre = block.parentElement;
            if (pre.parentElement.classList.contains('code-container')) return;

            // Check if Mermaid diagram
            if (block.classList.contains('language-mermaid')) {
                const code = block.textContent;
                const mermaidDiv = document.createElement('div');
                mermaidDiv.className = 'mermaid';
                mermaidDiv.textContent = code;
                pre.replaceWith(mermaidDiv);
                return;
            }

            const langClass = Array.from(block.classList).find(c => c.startsWith('language-'));
            const langName = langClass ? langClass.replace('language-', '').toUpperCase() : 'CODE';

            const container = document.createElement('div');
            container.className = 'code-container';

            const header = document.createElement('div');
            header.className = 'code-header';
            header.innerHTML = `<span>${'$'}{langName}</span><button class="copy-btn"><svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor"><path d="M0 6.75C0 5.784.784 5 1.75 5h1.5a.75.75 0 0 1 0 1.5h-1.5a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-1.5a.75.75 0 0 1 1.5 0v1.5A1.75 1.75 0 0 1 9.25 16h-7.5A1.75 1.75 0 0 1 0 14.25Z"></path><path d="M5 1.75C5 .784 5.784 0 6.75 0h7.5C15.216 0 16 .784 16 1.75v7.5A1.75 1.75 0 0 1 14.25 11h-7.5A1.75 1.75 0 0 1 5 9.25Zm1.75-.25a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-7.5a.25.25 0 0 0-.25-.25Z"></path></svg> Copy</button>`;

            const copyBtn = header.querySelector('.copy-btn');
            copyBtn.addEventListener('click', () => {
                const text = block.textContent;
                navigator.clipboard.writeText(text).then(() => {
                    copyBtn.innerHTML = `✓ Copied!`;
                    copyBtn.classList.add('copied');
                    setTimeout(() => {
                        copyBtn.innerHTML = `<svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor"><path d="M0 6.75C0 5.784.784 5 1.75 5h1.5a.75.75 0 0 1 0 1.5h-1.5a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-1.5a.75.75 0 0 1 1.5 0v1.5A1.75 1.75 0 0 1 9.25 16h-7.5A1.75 1.75 0 0 1 0 14.25Z"></path><path d="M5 1.75C5 .784 5.784 0 6.75 0h7.5C15.216 0 16 .784 16 1.75v7.5A1.75 1.75 0 0 1 14.25 11h-7.5A1.75 1.75 0 0 1 5 9.25Zm1.75-.25a.25.25 0 0 0-.25.25v7.5c0 .138.112.25.25.25h7.5a.25.25 0 0 0 .25-.25v-7.5a.25.25 0 0 0-.25-.25Z"></path></svg> Copy`;
                        copyBtn.classList.remove('copied');
                    }, 2000);
                });
            });

            pre.parentNode.insertBefore(container, pre);
            container.appendChild(header);
            container.appendChild(pre);
        });

        // Run Mermaid rendering
        mermaid.run({
            querySelector: '.mermaid'
        });
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeForJavaScriptTemplate(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("\r", "")
            .replace("</script>", "<\\/script>")
    }
}
