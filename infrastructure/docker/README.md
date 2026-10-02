# Build every image and start the whole platform.
# First run compiles 7 Spring Boot services plus the SPA, so expect 10-20
# minutes on a cold Maven cache.
docker compose up --build

# Start only the edge + infrastructure (useful when iterating on the SPA).
docker compose up postgres mongodb kafka kafka-init discovery-server api-gateway frontend

# Follow logs for one service.
docker compose logs -f transaction-service

# Tail structured logs going into Logstash.
docker compose logs -f logstash

# Tear down, keeping volumes (data survives).
docker compose down

# Full reset, including databases, Kafka topics and Elasticsearch indices.
docker compose down -v

# Validate the compose file without starting anything.
docker compose config --quiet
