# gpt-api-spec-completion

## Flow diagram
![flow](./docs/flow.png)

## Target mule project should meet below requirement
* run `mvn compile` without error
* Java 1.8
* Only one `.raml` file under `src/main/api/` folder
* A `schema` folder under  `src/main/api/` folder
* A `examples` folder under  `src/main/api/` folder
* All mule flow xml are in  `src/main/app/` folder
* A `dwl` folder under  `src/main/resources/` folder