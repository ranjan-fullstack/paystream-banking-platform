{{- define "paystream-service.fullname" -}}
{{- .Values.serviceName -}}
{{- end -}}

{{- define "paystream-service.labels" -}}
app: {{ .Values.serviceName }}
app.kubernetes.io/name: {{ .Values.serviceName }}
app.kubernetes.io/part-of: paystream-banking-platform
app.kubernetes.io/managed-by: Helm
{{- end -}}
