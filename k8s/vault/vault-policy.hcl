# Vault policy for product-service
# Only allows reading secrets for this specific service
path "secret/data/flipkart/product-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/flipkart/common/*" {
  capabilities = ["read"]
}
