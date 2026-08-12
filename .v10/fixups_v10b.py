from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
text = p.read_text(encoding='utf-8')

old = '''                .onSuccess { result ->
                    accuracyAutoTuner.refresh(accuracyValidationLab.matched(), force = true)
                    toast(result.label())
                    showAccuracyValidationLab(this, accuracyValidationLab) { accuracyCsvImport.launch("text/*") }
                }
'''
new = '''                .onSuccess { result ->
                    accuracyAutoTuner.refresh(accuracyValidationLab.matched(), force = true)
                    toast(result.label())
                    mainHandler.post { openAccuracyValidationLab() }
                }
'''
if text.count(old) != 1:
    raise RuntimeError(f'CSV callback anchor count={text.count(old)}')
text = text.replace(old, new, 1)

call = '        showAccuracyValidationLab(this, accuracyValidationLab) { accuracyCsvImport.launch("text/*") }\n'
if text.count(call) != 1:
    raise RuntimeError(f'Accuracy tool call count={text.count(call)}')
text = text.replace(call, '        openAccuracyValidationLab()\n', 1)

anchor = '''    private fun updateMetricCards(m: ShotMetrics) {
'''
method = '''    private fun openAccuracyValidationLab() {
        showAccuracyValidationLab(this, accuracyValidationLab) {
            accuracyCsvImport.launch("text/*")
        }
    }

    private fun updateMetricCards(m: ShotMetrics) {
'''
if text.count(anchor) != 1:
    raise RuntimeError(f'Accuracy method insertion anchor count={text.count(anchor)}')
text = text.replace(anchor, method, 1)
p.write_text(text, encoding='utf-8')
print('V10 CSV launcher fix applied')
