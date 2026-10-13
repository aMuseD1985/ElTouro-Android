// ElTouro Android shell. The app itself is the web app (https://…eltouro.de); this shell adds background recording.
// Which environment the APK talks to: ELTOURO_URL=https://app.eltouro.de npx cap sync android (default: the test server)
const url = (process.env.ELTOURO_URL || 'https://app.eltouro.de').replace(/\/+$/, '');
const host = new URL(url).host;

module.exports = {
  appId: 'de.eltouro.app',
  appName: 'ElTouro',
  webDir: 'www',
  server: {
    url,
    cleartext: false,
    allowNavigation: [host, '*.eltouro.de'],
    errorPath: 'error.html'
  },
  android: {
    allowMixedContent: false,
    backgroundColor: '#14263F'
  }
};
