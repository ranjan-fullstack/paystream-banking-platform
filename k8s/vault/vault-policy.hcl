# Vault policy for account-service
# Only allows reading secrets for this specific service
path "secret/data/paystream/account-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/paystream/common/*" {
  capabilities = ["read"]
}
