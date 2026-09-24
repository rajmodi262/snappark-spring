# Deploying to AWS (ECR + EC2)

This is the smallest real deployment: the image goes to **Amazon ECR**, and one **EC2** instance runs
it next to a PostgreSQL container. It fits in free-tier or new-account
credits. Delete everything afterwards (last section).

You run these steps with your own AWS account. Nothing here is automated against an account.

## 0. Prerequisites

- An AWS account and the AWS CLI v2, logged in (`aws configure` or `aws sso login`)
- Docker running locally
- Choose a region, e.g. `ap-south-1` (Mumbai)

```bash
export AWS_REGION=ap-south-1
export ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
export ECR_REGISTRY=$ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com
```

## 1. Push the image to ECR

```bash
aws ecr create-repository --repository-name snappark --image-scanning-configuration scanOnPush=true
aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker build -t "$ECR_REGISTRY/snappark:1.0.0" .
docker push "$ECR_REGISTRY/snappark:1.0.0"
```

`scanOnPush` makes ECR scan the image for known CVEs. Check the results in the ECR console.

## 2. Instance role and security group

```bash
# Role that lets the instance pull from ECR (no access keys stored on the box)
aws iam create-role --role-name snappark-ec2 --assume-role-policy-document \
  '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
aws iam attach-role-policy --role-name snappark-ec2 \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
aws iam create-instance-profile --instance-profile-name snappark-ec2
aws iam add-role-to-instance-profile --instance-profile-name snappark-ec2 --role-name snappark-ec2

# Allow only port 8080, and only from your own IP
MYIP=$(curl -s https://checkip.amazonaws.com)
SG=$(aws ec2 create-security-group --group-name snappark --description "SnapPark API" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$SG" --protocol tcp --port 8080 --cidr "$MYIP/32"
```

## 3. Launch the instance

Amazon Linux 2023 on `t3.micro`. The user-data script installs Docker, pulls the image and starts the stack.

```bash
cat > user-data.sh <<EOF
#!/bin/bash
dnf install -y docker && systemctl enable --now docker
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY
docker network create snappark
docker run -d --name db --network snappark --restart unless-stopped \
  -e POSTGRES_DB=snappark -e POSTGRES_USER=snappark -e POSTGRES_PASSWORD=change-this-db-password \
  -v pgdata:/var/lib/postgresql/data postgres:16-alpine
sleep 10
docker run -d --name app --network snappark --restart unless-stopped -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db:5432/snappark -e DB_USER=snappark -e DB_PASSWORD=change-this-db-password \
  $ECR_REGISTRY/snappark:1.0.0
EOF

AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)
aws ec2 run-instances --image-id "$AMI" --instance-type t3.micro --security-group-ids "$SG" \
  --iam-instance-profile Name=snappark-ec2 --user-data file://user-data.sh \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=snappark}]'
```

Change `change-this-db-password` before running this.

## 4. Verify

```bash
IP=$(aws ec2 describe-instances --filters Name=tag:Name,Values=snappark Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
curl http://$IP:8080/actuator/health/readiness        # {"status":"UP"}
curl "http://$IP:8080/api/v1/pricing/quote?type=CAR"
```

Container logs are ECS-format JSON (`docker logs app` on the instance). To collect them centrally,
run the container with the `awslogs` log driver to send them to CloudWatch Logs.

## Going further

- Move PostgreSQL to **Amazon RDS** and pass its endpoint in `DB_URL`.
- Run the image on **ECS Fargate** behind an Application Load Balancer. The readiness and liveness
  endpoints already exist for its health checks, and the service is stateless, so it can scale out.
- Let the Jenkins `Push to Amazon ECR` stage publish images by setting `ECR_REGISTRY` and `AWS_REGION`
  on an agent that has an ECR push role.

## Clean up (avoid charges)

```bash
aws ec2 terminate-instances --instance-ids $(aws ec2 describe-instances \
  --filters Name=tag:Name,Values=snappark --query 'Reservations[].Instances[].InstanceId' --output text)
aws ec2 delete-security-group --group-id "$SG"        # after the instance has terminated
aws ecr delete-repository --repository-name snappark --force
aws iam remove-role-from-instance-profile --instance-profile-name snappark-ec2 --role-name snappark-ec2
aws iam delete-instance-profile --instance-profile-name snappark-ec2
aws iam detach-role-policy --role-name snappark-ec2 --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
aws iam delete-role --role-name snappark-ec2
```
