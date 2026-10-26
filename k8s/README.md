# payment-service — Kubernetes local

A aplicação roda em cluster local (kind, minikube, Docker Desktop). Namespace
`payment-service`, um único Kustomize, sem overlays e sem GitOps.

**Oracle, MongoDB e Redis são externos ao cluster.** Nenhum deles é criado por estes
manifests — precisam estar no ar antes do `apply`, e o endereço deles entra pelo ConfigMap.

```
namespace.yaml     namespace
migracao-job.yaml  Job de migração do schema
deployment.yaml    aplicação
service.yaml       NodePort 30080
scaledobject.yaml  autoescala por KEDA
kustomization.yaml ConfigMap e Secret gerados
```

## Subir

```
docker build -t payment-service:latest .
kind load docker-image payment-service:latest        # ou minikube image load
kubectl apply -k k8s
kubectl -n payment-service wait --for=condition=complete job/payment-service-migracao --timeout=10m
```

Aplicação em `http://localhost:30080`, Swagger em `/swagger-ui.html`.

Derrubar: `kubectl delete -k k8s`. Os bancos externos não são tocados.

## Alcançar os bancos externos

Os literais do ConfigMap usam `host.docker.internal`, que resolve para a máquina hospedeira
no **Docker Desktop** (macOS e Windows). Em outros ambientes, trocar em
`kustomization.yaml`:

| Ambiente | Endereço da hospedeira |
|---|---|
| Docker Desktop (kind / Docker Desktop k8s) | `host.docker.internal` |
| minikube | `host.minikube.internal` |
| kind no Linux | IP da bridge do Docker (`docker network inspect kind -f '{{(index .IPAM.Config 0).Gateway}}'`) |
| banco em outra máquina | hostname ou IP dele |

Se o banco estiver em container Docker na mesma máquina, publicar a porta no host
(`-p 1521:1521`) — a rede do cluster não enxerga a rede do compose.

## Migração do banco

**Migrations são responsabilidade deste Job, não da aplicação.** `spring.flyway.enabled` é
`false` em `application.yaml` e só liga em `application-migration.yaml`.

| Recurso | `SPRING_PROFILES_ACTIVE` | Migra? |
|---|---|---|
| `deployment.yaml` | `local` | não, valida com `ddl-auto: validate` |
| `migracao-job.yaml` | `migration` | sim, e encerra |

A regra não é só convenção: `MigrationProfileGuard` recusa a subida se o Flyway estiver ligado
fora do perfil `migration`, se o perfil `migration` subir com o Flyway desligado — um Job que
sai `0` sem migrar é pior que um Job que quebra — ou se os dois perfis estiverem ativos juntos.
Detalhe em [docs/perfis.md](../docs/perfis.md).

O Job usa **a mesma imagem** da aplicação. No perfil `migration` o Boot sobe sem servidor web
(`spring.main.web-application-type=none`), o Flyway migra durante o refresh do contexto e o
processo encerra: saída `0` em sucesso, diferente de `0` em falha — que é o que o Job lê.

As migrations vivem em `src/main/resources/db/migration` e viajam dentro do jar. Não há cópia
em ConfigMap, então não existe versão divergente.

Banco fora do ar na hora do `apply`: o Flyway reconecta sozinho (`connect-retries: 20`, a
cada 10s) e o Job ainda tem `backoffLimit: 6`. Não há `initContainer` de espera — subir o
banco é pré-requisito, não responsabilidade deste chart.

A aplicação sobe com `ddl-auto: validate`. Se o Job não tiver terminado, o pod falha no
startup e entra em `CrashLoopBackOff` até a migração completar — depois se recupera sozinho.
Isso é intencional: melhor cair do que atender com schema errado.

Rodar a migração de novo, sem recriar o resto:

```
kubectl -n payment-service delete job payment-service-migracao
kubectl apply -k k8s
```

Flyway é idempotente: script já registrado em `flyway_schema_history` não é reaplicado.

O schema `PAY` e o usuário dono **não** são criados pelas migrations (`create-schemas: false`).
Em Oracle, schema é usuário — criar é tarefa de quem provisiona a instância.

## Autoescala — KEDA

`scaledobject.yaml` é um `ScaledObject` e substitui o HPA. Gatilhos `cpu` a 70% de utilização
e `memory` a 80%, de 1 a 3 réplicas. `scaleUp` sobe até 2 pods a cada 30s, `scaleDown` desce
1 pod a cada 60s com janela de estabilização de 180s.

O KEDA cria e governa o HPA. O `Deployment` **não** declara `replicas`, senão os dois brigam.

**Pré-requisito:** o operador do KEDA precisa estar no cluster, senão `kubectl apply -k`
falha em `no matches for kind "ScaledObject"`.

```
helm repo add kedacore https://kedacore.github.io/charts
helm upgrade --install keda kedacore/keda --namespace keda --create-namespace
```

Os gatilhos `cpu` e `memory` dependem do metrics-server. No kind, instalar:

```
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
```

Gatilho de fila ou de taxa de requisição não está declarado: o serviço não consome fila e não
há Prometheus no cluster local.

## Configuração

`application.yaml` é inteiramente preenchido por variáveis: toda propriedade é
`${VARIAVEL:default}`. A única exceção é `spring.flyway.enabled: false`, fixa de propósito —
migrations são do Job, e virar variável convidaria a devolver isso para a aplicação.

As 60 variáveis vivem em [`payment-service.env`](payment-service.env), que o
`configMapGenerator` consome por `envs:`. Esse arquivo é a configuração efetiva legível:
`kubectl get cm payment-service-config -o yaml` mostra tudo que está valendo, sem abrir o jar.

Quatro credenciais ficam fora dele, no `secretGenerator`:

| Variável | Origem |
|---|---|
| `PAYMENT_DB_USER`, `PAYMENT_DB_PASSWORD` | Secret `payment-service-credenciais` |
| `MONGODB_USERNAME`, `MONGODB_PASSWORD` | idem |

Grupos no `.env`: aplicação, banco relacional, MongoDB e Redis, bulkhead por domínio, circuito
por operação, retry por operação.

`ConfigMapCoverageTest` falha se uma variável das properties não tiver entrada no ConfigMap nem
no Secret, se o ConfigMap carregar chave que ninguém lê, ou se uma credencial aparecer no
ConfigMap. Variável nova sem entrada cairia no default em silêncio — o teste existe para que
isso não passe.

Os defaults no `application.yaml` apontam para `localhost`, então `./gradlew bootRun` funciona
direto contra os bancos externos, sem perfil e sem nenhuma variável exportada.

## Verificar

```
kubectl kustomize k8s
kubectl -n payment-service logs job/payment-service-migracao
kubectl -n payment-service get scaledobject,hpa,pod
```
