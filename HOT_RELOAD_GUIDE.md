# Hot Reload / Auto-Refresh Guide

This guide explains how to enable automatic browser refresh when you update your code.

## Current Setup

✅ **Spring Boot DevTools** is already included in `pom.xml`  
✅ **Thymeleaf cache** is disabled (`spring.thymeleaf.cache=false`)  
✅ **Live reload script** has been added to `app.html`

## Option 1: Local Development (Recommended for Hot Reload)

Run the application locally (not in Docker) for the best hot reload experience:

```bash
# Make sure you have MySQL running locally or update application.properties
mvn spring-boot:run
```

**How it works:**
- DevTools automatically detects changes in `src/main/java` and `src/main/resources`
- When you save a file, Spring Boot automatically restarts
- The browser will auto-refresh via the LiveReload script

**Browser Setup:**
1. Install the **LiveReload** browser extension:
   - Chrome: [LiveReload](https://chrome.google.com/webstore/detail/livereload/jnihajbhpnppcggjgedjdllkehhihmek)
   - Firefox: [LiveReload](https://addons.mozilla.org/en-US/firefox/addon/livereload-web-extension/)
2. Click the LiveReload icon in your browser to activate it
3. The page will automatically refresh when you save changes

## Option 2: Manual Browser Refresh

If auto-refresh doesn't work, you can:

1. **Use Browser DevTools:**
   - Press `F12` to open DevTools
   - Right-click the refresh button
   - Select "Empty Cache and Hard Reload"

2. **Use Keyboard Shortcuts:**
   - `Ctrl + Shift + R` (Windows/Linux) or `Cmd + Shift + R` (Mac) - Hard refresh
   - `Ctrl + F5` (Windows/Linux) or `Cmd + R` (Mac) - Normal refresh

3. **Disable Browser Cache (for development):**
   - Open Chrome DevTools (`F12`)
   - Go to Network tab
   - Check "Disable cache"
   - Keep DevTools open while developing

## Troubleshooting

### Changes not reflecting?

1. **Check if DevTools is running:**
   - Look for "LiveReload connected" message in browser console
   - Check if port 35729 is accessible

2. **Verify Thymeleaf cache is disabled:**
   ```properties
   spring.thymeleaf.cache=false
   ```

3. **Clear browser cache:**
   - `Ctrl + Shift + Delete` → Clear cached images and files

4. **Restart the application:**
   - Stop and restart Spring Boot
   - Rebuild if using Docker: `docker-compose build --no-cache`

5. **Check file paths:**
   - Make sure you're editing files in `src/main/resources/templates/` for HTML
   - Make sure you're editing files in `src/main/java/` for Java code

## Quick Reference

| Scenario | Command | Hot Reload? |
|----------|---------|-------------|
| Local Development | `mvn spring-boot:run` | ✅ Yes (with LiveReload extension) |

## Recommended Workflow

1. **For active development:** Use local Maven (`mvn spring-boot:run`) with LiveReload extension
2. **For production:** Build and deploy JAR file (no hot reload)
