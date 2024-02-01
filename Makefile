IMAGE_NAME = csa-bot

CONTAINGER_NAME_2023 = csa-bot-2023
CONTAINGER_NAME_2024 = csa-bot-2024

CONF = ${PWD}/../edu-csa-internal

DB_2023 = ${PWD}/../csa-db-2023
DB_2024 = ${PWD}/../csa-db-2024

PLAGIARISM_DB = ${PWD}/../plagiarism-db

BACKUP_PATH = ..

NOW = $(shell date +'%Y-%m-%d-%H-%M')


.PHONY: all format format-check lint test build run-jar run update pull backup docker-pull docker-build docker-run docker-stop clean repl

all: format test lint build

format:
	clojure -M:fmt fix

format-check:
	clojure -M:fmt check

lint:
	clojure -M:lint

test:
	clojure -X:test

build:
	clojure -X:build uber

run-jar: build
	CONF=../edu-csa-internal/csa-test.edn java -jar target/csa-bot.jar

run:
	CONF=../edu-csa-internal/csa-test.edn clojure -X course-bot.csa/-main

pull:
	git pull

backup:
	tar -zcf "${BACKUP_PATH}/2023-csa-db-snapshot-${NOW}.tar.gz" -C ${DB_2023} .
#   file should be more than 100 Kb
	[ `stat -c %s "${BACKUP_PATH}/2023-csa-db-snapshot-${NOW}.tar.gz"` -gt 100 ]

	tar -zcf "${BACKUP_PATH}/2024-csa-db-snapshot-${NOW}.tar.gz" -C ${DB_2024} .
#   file should be more than 100 Kb
	[ `stat -c %s "${BACKUP_PATH}/2024-csa-db-snapshot-${NOW}.tar.gz"` -gt 100 ]

	tar -zcf "${BACKUP_PATH}/plagiarism-db-${NOW}.tar.gz" -C ${PLAGIARISM_DB} .
#   file should be more than 100 Kb
	[ `stat -c %s "${BACKUP_PATH}/plagiarism-db-${NOW}.tar.gz"` -gt 100 ]

docker-build: build
	docker build -t ${IMAGE_NAME} .

update-2024: pull backup build docker-build docker-stop-2024 docker-run-2024

docker-run-2024:
	docker run --restart=always -d \
		-p 3100:3100 \
		-v ${CONF}:/edu-csa-internal \
		-e CONF='../edu-csa-internal/csa-2024.edn' \
		-v ${PLAGIARISM_DB}:/plagiarism-db \
		-v ${DB_2024}:/csa-db \
		--name ${CONTAINGER_NAME_2024} \
		${IMAGE_NAME}

docker-stop-2024:
	docker stop ${CONTAINGER_NAME_2024}
	docker rm ${CONTAINGER_NAME_2024}

update-2023: pull backup build docker-build docker-stop-2023 docker-run-2023

docker-run-2023:
	docker run --restart=always -d \
		-v ${CONF}:/edu-csa-internal \
		-e CONF='../edu-csa-internal/csa-2023.edn' \
		-v ${PLAGIARISM_DB}:/plagiarism-db \
		-v ${DB_2023}:/csa-db \
		--name ${CONTAINGER_NAME_2023} \
		${IMAGE_NAME}

docker-stop-2023:
	docker stop ${CONTAINGER_NAME_2023}
	docker rm ${CONTAINGER_NAME_2023}

clean:
	rm -fvr logs
	rm -fvr target
	rm -fvr tmp

repl:
	clojure -M:test:nrepl
