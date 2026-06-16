FROM golang:1.23-alpine AS build

WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/gold-api ./cmd/gold-api
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/gold-worker ./cmd/gold-worker

FROM alpine:3.20

RUN adduser -D -H gold
WORKDIR /app
COPY --from=build /out/gold-api /app/gold-api
COPY --from=build /out/gold-worker /app/gold-worker
USER gold

EXPOSE 8080
CMD ["/app/gold-api"]
