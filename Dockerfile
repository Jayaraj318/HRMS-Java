FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN mkdir -p out

RUN javac -cp "lib/*" -d out $(find src -name "*.java")

EXPOSE 8080

CMD ["java","-cp","out:lib/*","com.hrms.server.AppServer"]