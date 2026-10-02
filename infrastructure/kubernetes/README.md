# ======================================================================
# FINOVA — Kubernetes
#
# Order matters: namespace and configuration must exist before anything
# else, and the databases must be running before the services that need
# them become schedulable.
# ======================================================================

# 0. Secrets — never committed. Create from the example template.
kubectl apply -f infrastructure/kubernetes/namespace.yaml
kubectl create secret generic finova-secrets \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=DB_PASSWORD="<strong-password>" \
  --from-literal=MONGODB_ROOT_PASSWORD="<strong-password>" \
  --from-literal=MONGODB_URI="mongodb://finova:<strong-password>@mongodb:27017/finova_fraud?authSource=admin" \
  -n finova

# 1. Configuration
kubectl apply -f infrastructure/kubernetes/configmaps/
kubectl apply -f infrastructure/kubernetes/databases/init-configmap.yaml

# 2. Backing services (PostgreSQL, MongoDB, Kafka)
kubectl apply -f infrastructure/kubernetes/databases/stateful.yaml
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=postgres -n finova --timeout=300s
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=mongodb -n finova --timeout=300s
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=kafka   -n finova --timeout=300s

# 3. Provision Kafka topics (auto-creation is deliberately disabled)
kubectl run finova-kafka-init --rm -i --restart=Never \
  --image=apache/kafka:3.8.0 --namespace=finova \
  --overrides='{"spec":{"containers":[{"name":"init","image":"apache/kafka:3.8.0","command":["/bin/bash","-c","cp /scripts/create-topics.sh /tmp/t.sh && KAFKA_BOOTSTRAP=kafka-bootstrap:9092 bash /tmp/t.sh"],"volumeMounts":[{"name":"s","mountPath":"/scripts"}]}],"volumes":[{"name":"s","configMap":{"name":"finova-kafka-topics"}}]}}' \
  -- /bin/bash

# 4. Services and edge
kubectl apply -f infrastructure/kubernetes/services/
kubectl apply -f infrastructure/kubernetes/deployments/

# 5. Ingress
kubectl apply -f infrastructure/kubernetes/ingress.yaml

# 6. Observe
kubectl get pods -n finova -w
kubectl rollout status deployment/api-gateway -n finova
kubectl logs -f deployment/transaction-service -n finova -c transaction-service
kubectl port-forward -n finova svc/api-gateway 8080:8080
kubectl port-forward -n finova svc/frontend  8081:80

# Roll back
kubectl rollout undo deployment/transaction-service -n finova
kubectl rollout history deployment/transaction-service -n finova

# Remove
kubectl delete namespace finova
```

## What each manifest gives you

| Concern        | Decision                                                                       |
|----------------|--------------------------------------------------------------------------------|
| Availability   | 2–3 replicas per service, `maxUnavailable: 0`, PDBs                            |
| Health         | startup + readiness + liveness probes on the Actuator health groups            |
| Shutdown       | 45s grace period and a 15s `preStop` sleep so a rolling deploy drops no request |
| Resources      | Requests and limits on every container, bounded by a namespace LimitRange      |
| Scheduling     | `topologySpreadConstraints` spreads replicas across nodes                      |
| Security       | non-root, read-only root filesystem, all capabilities dropped, seccomp default   |
| Secrets        | Kubernetes Secrets (or External Secrets); never in a ConfigMap, never committed |
| Database       | one database per bounded context, created by an init hook                       |
| Messaging      | Kafka with auto-creation off; topics provisioned explicitly with retention       |
| Traffic        | one Ingress rule set; microservices stay ClusterIP-only so the gateway cannot be bypassed |

## Production notes

1. Replace the in-cluster PostgreSQL/MongoDB/Kafka with managed equivalents
   (RDS, DocumentDB, MSK) or operators (CloudNativePG, Percona, Strimzi).
2. Issue `finova-secrets` from a vault via External Secrets, not `kubectl create`.
3. Scale `discovery-server` to 3 replicas; Eureka's peer replication is
   already configured in `application.yml`.
4. Use a separate internal Ingress for `/actuator` and `/actuator/prometheus`
   with mTLS. The public Ingress here exposes them only for the demo.
5. Add a HPA per service driven by CPU plus request latency.
