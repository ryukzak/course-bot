NAME = csa-bot
DB = ${PWD}/../csa-db
CONF = ${PWD}/../edu-csa-internal

all: build stop deploy

build:
	docker build -t ${NAME} .

stop:
	docker stop ${NAME}
	docker rm ${NAME}

deploy:
	docker run --name ${NAME} --restart=always -d -v ${CONF}:/edu-csa-internal -v ${DB}:/csa-db ${NAME}

backup:
	tar -zcf "csa-db-snapshot-$(date +'%Y-%m-%d-%H-%M').tar.gz" ${DB}
