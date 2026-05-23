/**
 * Builds the multipart payload for `POST rest/service/prov/<sub>/upload`
 * from the InstanceImportDialog's form state.
 *
 * Pure helper — extracted from the dialog so unit tests can verify the
 * wire shape without mounting Vuetify's v-dialog (which teleports its
 * body and complicates assertion).
 *
 * Returns `null` when no file is selected, signalling the caller to
 * skip the upload. Mirrors the legacy field names exactly so the
 * backend's CSV parser sees what it expects:
 *   - `csv-file` (binary)
 *   - `separator`, `encoding`
 *   - `mergeUpload`         (UPDATE | KEEP | INSERT)
 *   - `memoryUnit`          ('1' = MB, '1024' = GB)
 *   - `headers-included`    ('true' | 'false')
 *   - `errorContinue`       ('true' | 'false')
 */
export function buildInstanceUploadFormData(form) {
  if (!form) return null
  const file = Array.isArray(form.file) ? form.file[0] : form.file
  if (!file) return null
  const fd = new FormData()
  fd.append('csv-file', file)
  fd.append('separator', form.separator)
  if (form.encoding) fd.append('encoding', form.encoding)
  fd.append('mergeUpload', form.mergeUpload)
  fd.append('memoryUnit', String(form.memoryUnit))
  fd.append('headers-included', String(form.headersIncluded))
  fd.append('errorContinue', String(form.errorContinue))
  return fd
}
